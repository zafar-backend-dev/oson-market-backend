package uz.osonmarket.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.branch.BranchService;
import uz.osonmarket.branch.BranchAdminController.Location;

@Service
public class OrderOperationsService {
    private final jakarta.persistence.EntityManager em;
    private final CustomerOrderRepository orders;
    private final OrderNotificationRepository notifications;
    private final MessageSource messages;
    private final OrderStatusHistoryRepository history;
    private final BranchService branches;
    private final OrderStatusEmailRepository emails;
    private final ApplicationEventPublisher events;

    OrderOperationsService(CustomerOrderRepository orders, OrderNotificationRepository notifications,
                           MessageSource messages, OrderStatusHistoryRepository history, BranchService branches,
                           OrderStatusEmailRepository emails, ApplicationEventPublisher events, jakarta.persistence.EntityManager em) {
        this.em = em;
        this.orders = orders;
        this.notifications = notifications;
        this.messages = messages;
        this.history = history;
        this.branches = branches;
        this.emails = emails;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PagedModel<AdminRow> list(OrderStatus status, String orderNumber, Pageable pageable) {
        String number = orderNumber == null || orderNumber.isBlank() ? null : orderNumber.strip().toUpperCase(Locale.ROOT);
        var page = orders.findForAdmin(status == null ? null : status.name(), number, page(pageable, "placedAt"));
        return new PagedModel<>(page.map(order -> new AdminRow(order.id, order.orderNumber, order.status,
                order.phone, order.telegramAddress, order.currency, order.grandTotal, order.placedAt,
                order.fulfillmentMethod, order.fulfillmentBranchId, order.paidAt)));
    }

    @Transactional(readOnly = true)
    public AdminDetail get(UUID id, Locale locale) {
        return detail(orders.findById(id).orElseThrow(OrderOperationsService::notFound), locale);
    }

    @Transactional
    public AdminDetail change(UUID id, AdminOrderController.StatusRequest request, UUID actorId, Locale locale) {
        var order = orders.findForUpdate(id).orElseThrow(OrderOperationsService::notFound);
        var target = request.status();
        String reason = request.reason() == null ? null : request.reason().strip();
        if (target.name().equals(order.status)) {
            if (target == OrderStatus.CANCELLED && !java.util.Objects.equals(reason, order.cancellationReason)) {
                throw new AuthException(HttpStatus.CONFLICT, "order.status.changed");
            }
            return detail(order, locale);
        }
        checkExpected(order, request.expectedStatus());
        if (!OrderStatus.next(order.status, order.fulfillmentMethod).contains(target)) {
            throw new AuthException(HttpStatus.CONFLICT, "order.status.transition");
        }
        if ((target == OrderStatus.CANCELLED || target == OrderStatus.DELIVERY_FAILED || target == OrderStatus.NEW_PAID)
                && (reason == null || reason.isBlank())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "order.status.reason.required");
        }
        recordStatus(order, target, actorId, reason);
        return detail(order, locale);
    }

    @Transactional
    public void cancelUnpaidForDeletion(UUID owner) {
        boolean hasPayments = Boolean.TRUE.equals((Boolean) em.createNativeQuery("select to_regclass('payments') is not null").getSingleResult());
        for (var order : orders.findUnpaidForDeletion(owner)) {
            if (hasPayments && Boolean.TRUE.equals((Boolean) em.createNativeQuery("""
                    SELECT EXISTS (SELECT 1 FROM payments WHERE order_id = :id
                      AND status NOT IN ('FAILED', 'CANCELLED', 'REFUNDED'))
                    """).setParameter("id", order.id).getSingleResult())) continue;
            recordStatus(order, OrderStatus.CANCELLED, null, "Account deletion requested");
        }
    }

    void recordStatus(CustomerOrder order, OrderStatus target, UUID actorId, String reason) {
        Instant now = Instant.now();
        var entry = new OrderStatusHistory();
        entry.orderId = order.id;
        entry.actorId = actorId;
        entry.oldStatus = order.status;
        entry.newStatus = target.name();
        entry.note = reason;
        entry.createdAt = now;
        history.save(entry);
        if (target == OrderStatus.CANCELLED) order.cancellationReason = reason;
        if (target == OrderStatus.NEW_PAID) order.paidAt = now;
        order.status = target.name();
        notifyStatus(order, target, reason, now);
    }

    void notifyStatus(CustomerOrder order, OrderStatus target, String reason, Instant now) {
        if (order.user != null) {
            var notification = new OrderNotification();
            notification.userId = order.user.getId();
            notification.order = order;
            notification.status = target;
            notification.createdAt = now;
            notification.reason = target == OrderStatus.CANCELLED || target == OrderStatus.DELIVERY_FAILED ? reason : null;
            notifications.save(notification);
            events.publishEvent(new OrderStatusEvent(notification.userId, notification.id, order.id, target, now));
            if (order.user.getEmail() != null && order.user.getEmailVerifiedAt() != null) {
                var email = new OrderStatusEmail();
                email.recipient = order.user.getEmail();
                email.language = order.user.getPreferredLanguage() == null ? "uz" : order.user.getPreferredLanguage().getCode();
                email.orderNumber = order.orderNumber;
                email.status = target;
                email.reason = notification.reason;
                email.nextAttemptAt = now;
                emails.save(email);
            }
        }
    }

    @Transactional
    public AdminDetail fulfillment(UUID id, AdminOrderController.FulfillmentRequest request, Locale locale) {
        var order = orders.findForUpdate(id).orElseThrow(OrderOperationsService::notFound);
        checkExpected(order, request.expectedStatus());
        if (order.paidAt != null || !List.of("PENDING", "NEW_UNPAID", "CONFIRMED").contains(order.status)) {
            throw new AuthException(HttpStatus.CONFLICT, "order.fulfillment.locked");
        }
        applyFulfillment(order, request.method(), request.branchId(), request.deliveryAddress(), request.location());
        notifyOrderChanged(order);
        return detail(order, locale);
    }

    void applyFulfillment(CustomerOrder order, FulfillmentMethod method, UUID branchId, String address, Location location) {
        order.fulfillmentMethod = method;
        order.fulfillmentBranchId = branchId;
        if (method == FulfillmentMethod.DELIVERY) {
            if (location == null || address == null || address.isBlank()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "order.delivery.location.required");
            }
            var quote = branches.quote(branchId, location);
            order.deliveryAddress = address.strip();
            order.deliveryLatitude = location.latitude();
            order.deliveryLongitude = location.longitude();
            order.deliveryDistanceKm = quote.distanceKm();
            order.deliveryPricePerKm = quote.pricePerKm();
            order.shippingTotal = quote.deliveryPrice();
        } else {
            branches.requirePickup(branchId);
            order.deliveryAddress = null;
            order.deliveryLatitude = null;
            order.deliveryLongitude = null;
            order.deliveryDistanceKm = null;
            order.deliveryPricePerKm = null;
            order.shippingTotal = BigDecimal.ZERO;
        }
        BigDecimal total = order.subtotal.subtract(order.discountTotal == null ? BigDecimal.ZERO : order.discountTotal)
                .add(order.shippingTotal);
        if (total.setScale(2).precision() > 19) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "order.total.invalid");
        }
        order.grandTotal = total;
    }

    private static void checkExpected(CustomerOrder order, OrderStatus expected) {
        if (!expected.name().equals(order.status)) {
            throw new AuthException(HttpStatus.CONFLICT, "order.status.changed");
        }
    }

    @Transactional(readOnly = true)
    public PagedModel<Notification> inbox(UUID owner, Pageable pageable, Locale locale) {
        return new PagedModel<>(notifications.findByUserId(owner, page(pageable, "createdAt")).map(row -> {
            String status = messages.getMessage("order.status." + row.status.name(), null, locale);
            String orderNumber = row.order.getOrderNumber();
            boolean rating = row.status == OrderStatus.DELIVERED;
            return new Notification(row.id, "ORDER_STATUS_CHANGED", row.order.getId(), orderNumber,
                    row.status, row.createdAt, row.readAt, row.reason, rating ? "RATE_SERVICE" : null,
                    messages.getMessage(rating ? "order.rating.title" : "order.notification.title", null, locale),
                    messages.getMessage(rating ? "order.rating.message" : "order.notification.message",
                            new Object[]{orderNumber, status}, locale));
        }));
    }

    @Transactional(readOnly = true)
    public long unread(UUID owner) {
        return notifications.countByUserIdAndReadAtIsNull(owner);
    }

    @Transactional
    public void read(UUID owner, UUID id) {
        if (notifications.markRead(id, owner, Instant.now()) == 0 && !notifications.existsByIdAndUserId(id, owner)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "order.notification.notFound");
        }
    }

    @Transactional
    public void readAll(UUID owner) {
        notifications.markAllRead(owner, Instant.now());
    }

    private static Pageable page(Pageable pageable, String dateField) {
        return PageRequest.of(pageable.getPageNumber(), Math.min(100, pageable.getPageSize()),
                Sort.by(Sort.Order.desc(dateField), Sort.Order.desc("id")));
    }

    private AdminDetail detail(CustomerOrder order, Locale locale) {
        return new AdminDetail(OrderService.view(order, locale.getLanguage()),
                OrderStatus.next(order.status, order.fulfillmentMethod), order.fulfillmentMethod,
                order.fulfillmentBranchId, order.deliveryAddress, order.deliveryLatitude, order.deliveryLongitude,
                order.deliveryDistanceKm, order.deliveryPricePerKm, order.cancellationReason, order.paidAt,
                history.findByOrderIdOrderByCreatedAtAscIdAsc(order.id).stream()
                        .map(entry -> new History(entry.oldStatus, entry.newStatus, entry.actorId, entry.note, entry.createdAt))
                        .toList());
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "order.notFound");
    }

    public record AdminRow(UUID id, String orderNumber, String status, String phone, String telegramAddress,
                           String currency, BigDecimal grandTotal, Instant placedAt,
                           FulfillmentMethod fulfillmentMethod, UUID branchId, Instant paidAt) {}
    public record AdminDetail(OrderView order, List<OrderStatus> allowedStatuses, FulfillmentMethod fulfillmentMethod,
                              UUID branchId, String deliveryAddress, BigDecimal latitude, BigDecimal longitude,
                              BigDecimal distanceKm, BigDecimal pricePerKm, String cancellationReason,
                              Instant paidAt, List<History> history) {}
    public record History(String previousStatus, String status, UUID actorId, String reason, Instant createdAt) {}
    public record Notification(UUID id, String type, UUID orderId, String orderNumber, OrderStatus status,
                               Instant createdAt, Instant readAt, String reason, String action, String title, String message) {}

    void notifyOrderChanged(CustomerOrder order) {
        events.publishEvent(new OrderChangedEvent(order.user == null ? null : order.user.getId(), order.id));
    }

    record OrderChangedEvent(UUID owner, UUID orderId) {}
    record OrderStatusEvent(UUID owner, UUID notificationId, UUID orderId, OrderStatus status, Instant createdAt) {}
}
