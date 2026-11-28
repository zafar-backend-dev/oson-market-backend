package uz.osonmarket.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.osonmarket.order.OrderOperationsService;

@Service
public class AccountDeletionService {
    private final JdbcTemplate db;

    private final OrderOperationsService orderOperations;

    public AccountDeletionService(JdbcTemplate db, OrderOperationsService orderOperations) {
        this.db = db;
        this.orderOperations = orderOperations;

    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void eraseIdentity(UUID accountId) {
        var requests = db.queryForList("""
                SELECT status FROM account_deletion_requests WHERE account_id = ? FOR UPDATE
                """, String.class, accountId);
        if (requests.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if ("COMPLETED".equals(requests.get(0))) return;
        var emails = db.queryForList("""
                SELECT email FROM users WHERE id = ? AND role = 'USER' AND status = 'DELETION_PENDING' FOR UPDATE
                """, String.class, accountId);
        if (!emails.isEmpty()) eraseAccount(accountId, emails.get(0));
        else if (Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)", Boolean.class, accountId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        db.update("UPDATE account_deletion_requests SET status = 'PROCESSING' WHERE account_id = ?", accountId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean advance(UUID accountId) {
        String status = db.queryForObject("""
                SELECT status FROM account_deletion_requests WHERE account_id = ? FOR UPDATE
                """, String.class, accountId);
        if ("COMPLETED".equals(status)) return true;

        var orders = db.queryForList("""
                SELECT o.id FROM orders o JOIN account_deletion_orders d ON d.order_id = o.id
                WHERE d.account_id = ? ORDER BY o.id FOR UPDATE OF o
                """, UUID.class, accountId);
        boolean completed = true;
        for (UUID orderId : orders) {
            orderOperations.cancelDetachedUnpaidForDeletion(orderId);
            boolean terminal = terminalOrder(orderId);
            if (terminal) removeDeliveryAddress(orderId);
            if (terminal && financiallySettled(orderId)) {
                eraseOrderContact(orderId);
                db.update("DELETE FROM account_deletion_orders WHERE order_id = ?", orderId);
            } else {
                completed = false;
            }
        }
        db.update("""
                UPDATE account_deletion_requests SET status = ?,
                    completed_at = CASE WHEN ? THEN now() ELSE NULL END,
                    next_attempt_at = now() + interval '1 minute' WHERE account_id = ?
                """, completed ? "COMPLETED" : "PROCESSING", completed, accountId);
        return completed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID accountId) {
        db.update("""
                UPDATE account_deletion_requests SET status = 'FAILED', next_attempt_at = now() + interval '1 minute'
                WHERE account_id = ? AND status <> 'COMPLETED'
                """, accountId);
    }

    private void eraseAccount(UUID accountId, String email) {
        db.queryForList("SELECT id FROM orders WHERE user_id = ? ORDER BY id FOR UPDATE", UUID.class, accountId);
        db.update("""
                INSERT INTO account_deletion_orders (order_id, account_id)
                SELECT id, ? FROM orders WHERE user_id = ? ON CONFLICT (order_id) DO NOTHING
                """, accountId, accountId);
        var avatars = db.queryForList("""
                SELECT m.id FROM user_profiles p JOIN media_files m ON m.id = p.avatar_media_id
                WHERE p.user_id = ? AND m.storage_key LIKE 'profile/%'
                """, UUID.class, accountId);
        db.update("DELETE FROM mobile_chat_messages WHERE owner_id = ? OR sender_id = ?", accountId, accountId);
        db.update("""
                DELETE FROM product_comment_likes WHERE user_id = ?
                    OR comment_id IN (SELECT id FROM product_comments WHERE author_id = ?)
                """, accountId, accountId);
        db.update("""
                UPDATE product_comments SET author_id = NULL, body = '', deleted_at = COALESCE(deleted_at, now())
                WHERE author_id = ?
                """, accountId);
        optional("cart_items", "DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)", accountId);
        optional("carts", "DELETE FROM carts WHERE user_id = ?", accountId);
        optional("user_addresses", "DELETE FROM user_addresses WHERE user_id = ?", accountId);
        optional("favorites", "DELETE FROM favorites WHERE user_id = ?", accountId);
        optional("product_reviews", "DELETE FROM product_reviews WHERE user_id = ?", accountId);
        optional("product_questions", """
                UPDATE product_questions SET user_id = NULL, question = NULL WHERE user_id = ?
                """, accountId);
        optional("product_answers", "UPDATE product_answers SET author_user_id = NULL, answer = NULL WHERE author_user_id = ?", accountId);
        optional("notifications", "DELETE FROM notifications WHERE user_id = ?", accountId);
        optional("promo_redemptions", "UPDATE promo_redemptions SET user_id = NULL WHERE user_id = ?", accountId);
        optional("order_returns", "UPDATE order_returns SET user_id = NULL WHERE user_id = ?", accountId);
        db.update("""
                DELETE FROM order_telegram_deliveries WHERE split_part(message, chr(10), 1) = 'Oson Market | Yangi buyurtma'
                    AND split_part(message, chr(10), 2) IN (SELECT order_number FROM orders WHERE user_id = ?)
                """, accountId);
        db.update("DELETE FROM order_status_emails WHERE lower(recipient) = lower(?)", email);
        db.update("""
                UPDATE orders SET user_id = NULL, request_id = NULL, requested_fulfillment = NULL, telegram_address = NULL,
                    service_review = NULL, service_rating = NULL, rated_at = NULL, cancellation_reason = NULL
                WHERE user_id = ?
                """, accountId);
        for (String table : List.of("product_likes", "advertisement_notification_reads", "order_notifications",
                "order_push_devices", "user_devices", "refresh_tokens", "user_profiles")) {
            db.update("DELETE FROM " + table + " WHERE user_id = ?", accountId);
        }
        for (UUID avatarId : avatars) {
            db.update("DELETE FROM profile_avatars WHERE id = ?", avatarId);
            db.update("DELETE FROM media_files WHERE id = ?", avatarId);
        }
        db.update("DELETE FROM auth_verification_codes WHERE user_id = ? OR lower(email) = lower(?)", accountId, email);
        db.update("UPDATE order_status_history SET changed_by = NULL, note = NULL WHERE changed_by = ?", accountId);
        db.update("DELETE FROM users WHERE id = ?", accountId);
    }

    private boolean terminalOrder(UUID orderId) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT COALESCE(status IN ('DELIVERED', 'PICKED_UP', 'CANCELLED'), false)
                FROM orders WHERE id = ?
                """, Boolean.class, orderId));
    }

    private boolean financiallySettled(UUID orderId) {
        return !unsettled("payments", "'PAID', 'SUCCESS', 'SUCCEEDED', 'CAPTURED', 'COMPLETED', 'FAILED', 'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED'", orderId)
                && !unsettled("order_returns", "'COMPLETED', 'REFUNDED', 'REJECTED', 'CANCELLED'", orderId)
                && !hasActiveRefund(orderId);
    }

    private boolean hasActiveRefund(UUID orderId) {
        return exists("refunds") && Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM refunds r JOIN payments p ON p.id = r.payment_id
                    WHERE p.order_id = ? AND COALESCE(upper(r.status::text), '') NOT IN
                        ('COMPLETED', 'REFUNDED', 'SUCCESS', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED')
                )
                """, Boolean.class, orderId));
    }

    private boolean unsettled(String table, String terminalStates, UUID orderId) {
        return exists(table) && Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table + " obligation WHERE obligation.order_id = ?"
                        + " AND COALESCE(upper(to_jsonb(obligation)->>'status'), '') NOT IN (" + terminalStates + "))",
                Boolean.class, orderId));
    }

    private void eraseOrderContact(UUID orderId) {
        optional("order_returns", "UPDATE order_returns SET reason = NULL WHERE order_id = ?", orderId);
        db.update("UPDATE orders SET phone = NULL, cancellation_reason = NULL WHERE id = ?", orderId);
    }

    private void removeDeliveryAddress(UUID orderId) {
        optional("order_addresses", "DELETE FROM order_addresses WHERE order_id = ?", orderId);
        optional("order_delivery_addresses", "DELETE FROM order_delivery_addresses WHERE order_id = ?", orderId);
        db.update("""
                UPDATE orders SET delivery_address = NULL, delivery_latitude = NULL, delivery_longitude = NULL
                WHERE id = ? AND (delivery_address IS NOT NULL OR delivery_latitude IS NOT NULL OR delivery_longitude IS NOT NULL)
                """, orderId);
    }

    private boolean exists(String table) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }

    private void optional(String table, String sql, UUID accountId) {
        if (exists(table)) {
            db.update(sql, accountId);
        }
    }
}
