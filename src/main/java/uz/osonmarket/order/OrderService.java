package uz.osonmarket.order;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.catalog.customer.tree.CatalogTreeService;
import uz.osonmarket.catalog.price.CatalogPrices;
import uz.osonmarket.domain.catalog.ProductType;
import uz.osonmarket.domain.identity.User;
import uz.osonmarket.order.telegram.TelegramOrderQueue;

@Service
public class OrderService {
    private static final String UZS = "UZS";
    private final CustomerOrderRepository orders;
    private final EntityManager em;
    private final CatalogPrices prices;
    private final CatalogTreeService catalog;
    private final AuthLocks locks;
    private final TelegramOrderQueue notifications;
    private final OrderOperationsService operations;
    private final OrderStatusHistoryRepository history;

    public OrderService(CustomerOrderRepository orders, EntityManager em, CatalogPrices prices,
                        CatalogTreeService catalog, AuthLocks locks, TelegramOrderQueue notifications,
                        OrderOperationsService operations, OrderStatusHistoryRepository history) {
        this.orders = orders;
        this.em = em;
        this.prices = prices;
        this.catalog = catalog;
        this.locks = locks;
        this.notifications = notifications;
        this.operations = operations;
        this.history = history;
    }

    @Transactional
    public Result create(UUID userId, OrderRequest input, String language) {
        String phone = input.phone().strip().replaceAll("[ ()-]", "");
        String telegram = input.telegramAddress().strip().replaceFirst("(?i)^(?:https?://)?t\\.me/", "")
                .replaceFirst("^@", "").replaceFirst("/$", "").toLowerCase(Locale.ROOT);
        if (!phone.matches("\\+?[0-9]{7,15}") || !telegram.matches("[a-z][a-z0-9_]{4,31}")) {
            throw failure(HttpStatus.BAD_REQUEST, "order.contact.invalid");
        }
        telegram = "@" + telegram;
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderRequest.Item item : input.items()) {
            if (quantities.putIfAbsent(item.variantId(), item.quantity()) != null) {
                throw failure(HttpStatus.BAD_REQUEST, "order.items.duplicate");
            }
        }

        locks.lock("order:customer:" + userId);
        var fulfillment = input.fulfillment();
        String fulfillmentKey = fulfillmentKey(fulfillment);
        var prior = orders.findByUserIdAndRequestId(userId, input.requestId()).orElse(null);
        if (prior != null) {
            var original = new HashMap<UUID, Integer>();
            prior.items.forEach(item -> original.put(item.variant.getId(), item.quantity));
            if (!phone.equals(prior.phone) || !telegram.equals(prior.telegramAddress) || !quantities.equals(original)
                    || !Objects.equals(fulfillmentKey, prior.requestedFulfillment)) {
                throw failure(HttpStatus.CONFLICT, "order.request.conflict");
            }
            return new Result(view(prior, language), false);
        }

        var order = new CustomerOrder();
        if (fulfillment == null) {
            throw failure(HttpStatus.BAD_REQUEST, "order.fulfillment.required");
        }
        order.user = em.getReference(User.class, userId);
        order.requestId = input.requestId();
        order.requestedFulfillment = fulfillmentKey;
        order.orderNumber = "OM-" + UUID.randomUUID();
        order.phone = phone;
        order.telegramAddress = telegram;
        order.status = OrderStatus.NEW_UNPAID.name();
        order.currency = UZS;
        order.placedAt = Instant.now();
        order.subtotal = BigDecimal.ZERO;
        order.discountTotal = BigDecimal.ZERO;
        order.shippingTotal = BigDecimal.ZERO;

        for (OrderRequest.Item inputItem : input.items()) {
            var variant = em.find(ProductType.class, inputItem.variantId());
            if (variant == null || !catalog.isOrderable(variant)) {
                throw failure(HttpStatus.CONFLICT, "order.product.unavailable");
            }
            var price = prices.current(variant.getId());
            if (price == null) {
                throw failure(HttpStatus.CONFLICT, "order.product.unavailable");
            }
            var item = new CustomerOrderItem();
            item.order = order;
            item.variant = variant;
            item.skuSnapshot = variant.getSku();
            item.titleUzSnapshot = title(variant, "uz");
            item.titleRuSnapshot = title(variant, "ru");
            item.quantity = inputItem.quantity();
            item.unitPrice = price.regularPrice();
            BigDecimal count = BigDecimal.valueOf(item.quantity);
            item.lineTotal = price.amount().multiply(count);
            item.discountAmount = item.unitPrice.multiply(count).subtract(item.lineTotal);
            order.items.add(item);
            order.subtotal = order.subtotal.add(item.unitPrice.multiply(count));
            order.discountTotal = order.discountTotal.add(item.discountAmount);
        }
        order.grandTotal = order.subtotal.subtract(order.discountTotal);
        if (order.subtotal.setScale(2).precision() > 19) {
            throw failure(HttpStatus.BAD_REQUEST, "order.total.invalid");
        }
        operations.applyFulfillment(order, fulfillment.method(), fulfillment.branchId(),
                fulfillment.deliveryAddress(), fulfillment.location());
        orders.save(order);
        operations.notifyStatus(order, OrderStatus.NEW_UNPAID, null, order.placedAt);
        OrderView view = view(order, language);
        notifications.enqueue(view);
        return new Result(view, true);
    }

    private static String fulfillmentKey(OrderRequest.Fulfillment value) {
        if (value == null) return null;
        String key = value.method() + ":" + value.branchId();
        if (value.method() == FulfillmentMethod.PICKUP) return key;
        var point = value.location();
        String address = value.deliveryAddress() == null ? "" : value.deliveryAddress().strip();
        return point == null ? key + ":" + address
                : key + ":" + point.latitude().stripTrailingZeros().toPlainString() + ":"
                + point.longitude().stripTrailingZeros().toPlainString() + ":" + address;
    }

    @Transactional(readOnly = true)
    public PagedModel<OrderView> list(UUID owner, Pageable pageable, String language) {
        var page = PageRequest.of(pageable.getPageNumber(), Math.min(100, pageable.getPageSize()),
                Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));
        return new PagedModel<>(orders.findByUserId(owner, page).map(order -> view(order, language)));
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID owner, UUID id, String language) {
        return view(orders.findByIdAndUserId(id, owner)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "order.notFound")), language);
    }

    @Transactional(readOnly = true)
    public List<StatusEntry> history(UUID owner, UUID id) {
        var order = orders.findByIdAndUserId(id, owner)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "order.notFound"));
        var timeline = new ArrayList<StatusEntry>();
        var entries = history.findByOrderIdOrderByCreatedAtAscIdAsc(id);
        String initial = entries.isEmpty() ? order.status : entries.get(0).oldStatus;
        timeline.add(new StatusEntry(initial, null, order.placedAt));
        for (var entry : entries) {
            boolean showReason = "CANCELLED".equals(entry.newStatus) || "DELIVERY_FAILED".equals(entry.newStatus);
            timeline.add(new StatusEntry(entry.newStatus, showReason ? entry.note : null, entry.createdAt));
        }
        return timeline;
    }

    public record StatusEntry(String status, String reason, Instant createdAt) {}

    @Transactional
    public OrderView cancel(UUID owner, UUID id, String reason, String language) {
        var order = ownedForUpdate(owner, id);
        String note = reason.strip();
        if ("CANCELLED".equals(order.status) && note.equals(order.cancellationReason)) return view(order, language);
        if (!canCancel(order)) throw failure(HttpStatus.CONFLICT, "order.cancel.unavailable");
        operations.recordStatus(order, OrderStatus.CANCELLED, owner, note);
        return view(order, language);
    }

    @Transactional
    public OrderView rate(UUID owner, UUID id, OrderController.RatingRequest request, String language) {
        var order = ownedForUpdate(owner, id);
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        if (Objects.equals(order.serviceRating, request.rating()) && Objects.equals(order.serviceReview, comment)) {
            return view(order, language);
        }
        if (!canRate(order)) throw failure(HttpStatus.CONFLICT, "order.rating.unavailable");
        order.serviceRating = request.rating();
        order.serviceReview = comment;
        order.ratedAt = Instant.now();
        operations.notifyOrderChanged(order);
        return view(order, language);
    }

    private CustomerOrder ownedForUpdate(UUID owner, UUID id) {
        return orders.findOwnedForUpdate(id, owner).orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "order.notFound"));
    }

    private static boolean canCancel(CustomerOrder order) {
        return order.paidAt == null && ("NEW_UNPAID".equals(order.status) || "PENDING".equals(order.status));
    }

    private static boolean canRate(CustomerOrder order) {
        return "DELIVERED".equals(order.status) && order.serviceRating == null;
    }

    private String title(ProductType variant, String language) {
        var product = variant.getProduct();
        return em.createQuery("""
                select t.title from ProductTranslation t
                where t.product.id = :id and t.language.code in ('uz', 'ru')
                order by case when t.language.code = :language then 0 else 1 end
                """, String.class).setParameter("id", product.getId()).setParameter("language", language)
                .setMaxResults(1).getResultList().stream().findFirst().orElse(product.getSlug());
    }

    static OrderView view(CustomerOrder order, String language) {
        return new OrderView(order.id, order.orderNumber, order.status, order.currency, order.phone, order.telegramAddress,
                order.placedAt, order.subtotal, order.discountTotal, order.shippingTotal, order.grandTotal,
                order.items.stream().map(item -> new OrderView.Item(item.variant == null ? null : item.variant.getId(),
                        item.skuSnapshot, "ru".equals(language) ? item.titleRuSnapshot : item.titleUzSnapshot,
                        item.quantity, item.unitPrice, item.discountAmount, item.lineTotal)).toList(),
                order.fulfillmentMethod, order.fulfillmentBranchId, order.deliveryAddress, order.deliveryLatitude,
                order.deliveryLongitude, order.deliveryDistanceKm, order.deliveryPricePerKm, order.cancellationReason,
                order.paidAt, canCancel(order), canRate(order), order.serviceRating, order.serviceReview, order.ratedAt);
    }

    private AuthException failure(HttpStatus status, String code) {
        return new AuthException(status, code);
    }

    public record Result(OrderView order, boolean created) {}
}
