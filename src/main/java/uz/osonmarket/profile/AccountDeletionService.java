package uz.osonmarket.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountDeletionService {
    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;

    public AccountDeletionService(JdbcTemplate db, ApplicationEventPublisher events) {
        this.db = db;
        this.events = events;
    }

    @Transactional
    public void complete(UUID accountId, boolean obligationsSettled) {
        var requests = db.queryForList("""
                SELECT status FROM account_deletion_requests WHERE account_id = ?
                """, String.class, accountId);
        if (requests.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if ("COMPLETED".equals(requests.get(0))) return;
        var emails = db.queryForList("SELECT email FROM users WHERE id = ? AND role = 'USER' FOR UPDATE",
                String.class, accountId);
        String statusAfterLock = db.queryForObject("SELECT status FROM account_deletion_requests WHERE account_id = ? FOR UPDATE",
                String.class, accountId);
        if ("COMPLETED".equals(statusAfterLock)) return;
        if (emails.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer account unavailable");
        var states = db.queryForList("SELECT status FROM orders WHERE user_id = ? FOR UPDATE", String.class, accountId);
        if (states.stream().anyMatch(status -> status == null || !List.of("DELIVERED", "PICKED_UP", "CANCELLED").contains(status))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Active orders must be resolved first");
        }
        if (!states.isEmpty() && !obligationsSettled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Financial review required");
        }
        db.update("UPDATE account_deletion_requests SET status = 'PROCESSING' WHERE account_id = ?", accountId);
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
        optional("order_returns", "UPDATE order_returns SET user_id = NULL, reason = NULL WHERE user_id = ?", accountId);
        optional("order_addresses", "DELETE FROM order_addresses WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", accountId);
        optional("order_delivery_addresses", "DELETE FROM order_delivery_addresses WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", accountId);
        db.update("""
                DELETE FROM order_telegram_deliveries WHERE split_part(message, chr(10), 1) = 'Oson Market | Yangi buyurtma'
                    AND split_part(message, chr(10), 2) IN (SELECT order_number FROM orders WHERE user_id = ?)
                """, accountId);
        db.update("DELETE FROM order_status_emails WHERE lower(recipient) = lower(?)", emails.get(0));
        db.update("""
                UPDATE orders SET user_id = NULL, request_id = NULL, phone = NULL, telegram_address = NULL,
                    requested_fulfillment = NULL, delivery_address = NULL, delivery_latitude = NULL,
                    delivery_longitude = NULL, service_review = NULL, service_rating = NULL, rated_at = NULL,
                    cancellation_reason = NULL
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
        db.update("DELETE FROM auth_verification_codes WHERE user_id = ? OR lower(email) = lower(?)", accountId, emails.get(0));
        db.update("DELETE FROM users WHERE id = ?", accountId);
        db.update("""
                UPDATE account_deletion_requests SET status = 'COMPLETED', completed_at = now() WHERE account_id = ?
                """, accountId);
        events.publishEvent(new AccountRemoved(accountId));
    }

    private void optional(String table, String sql, UUID accountId) {
        if (Boolean.TRUE.equals(db.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table))) {
            db.update(sql, accountId);
        }
    }
}
