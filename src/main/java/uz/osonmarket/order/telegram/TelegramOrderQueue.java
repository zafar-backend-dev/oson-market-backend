package uz.osonmarket.order.telegram;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.order.OrderView;
import uz.osonmarket.order.telegram.TelegramRecipientController.Recipient;

@Service
public class TelegramOrderQueue {
    private final EntityManager em;
    private final AuthLocks locks;
    private final TelegramOrderSender sender;

    public TelegramOrderQueue(EntityManager em, AuthLocks locks, TelegramOrderSender sender) {
        this.em = em;
        this.locks = locks;
        this.sender = sender;
    }

    @Transactional(readOnly = true)
    public List<Recipient> recipients() {
        return allRecipients().stream().map(this::view).toList();
    }

    @Transactional
    public Recipient add(long chatId, String label) {
        locks.lock("order:telegram-recipient:" + chatId);
        if (hasRecipient(chatId)) {
            throw new AuthException(HttpStatus.CONFLICT, "order.recipient.exists");
        }
        var entity = new TelegramRecipient();
        entity.chatId = chatId;
        entity.label = label == null || label.isBlank() ? null : label.strip();
        em.persist(entity);
        return view(entity);
    }

    @Transactional
    public void remove(UUID id) {
        var entity = em.find(TelegramRecipient.class, id);
        if (entity == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "order.recipient.notFound");
        }
        em.remove(entity);
    }

    @Transactional
    public void enqueue(OrderView order) {
        var message = new StringBuilder("Oson Market | Yangi buyurtma\n")
                .append(order.orderNumber()).append("\n")
                .append("Telefon: ").append(order.phone()).append("\n")
                .append("Telegram: ").append(order.telegramAddress()).append("\n")
                .append("Jami: ").append(order.grandTotal().toPlainString()).append(" UZS\n");
        for (var item : order.items()) {
            String line = "\n" + item.quantity() + " × " + item.title() + " (" + item.sku() + ")";
            if (message.length() + line.length() > 3800) {
                message.append("\n…");
                break;
            }
            message.append(line);
        }
        for (var recipient : allRecipients()) {
            var delivery = new TelegramDelivery();
            delivery.chatId = recipient.chatId;
            delivery.message = message.toString();
            delivery.nextAttemptAt = Instant.now();
            em.persist(delivery);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> due() {
        return em.createQuery("select d.id from TelegramDelivery d where d.nextAttemptAt <= :now order by d.nextAttemptAt, d.id",
                        UUID.class).setParameter("now", Instant.now()).setMaxResults(20).getResultList();
    }

    @Transactional
    public void deliver(UUID id) {
        var delivery = em.find(TelegramDelivery.class, id, LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now();
        if (delivery == null || delivery.nextAttemptAt == null || delivery.nextAttemptAt.isAfter(now)) {
            return;
        }
        if (!hasRecipient(delivery.chatId)) {
            em.remove(delivery);
            return;
        }
        delivery.attempts++;
        if (sender.send(delivery.chatId, delivery.message)) {
            delivery.sentAt = Instant.now();
            delivery.nextAttemptAt = null;
            delivery.message = "";
        } else if (delivery.attempts < 5) {
            delivery.nextAttemptAt = Instant.now().plusSeconds(30L << (delivery.attempts - 1));
        } else {
            delivery.nextAttemptAt = null;
            delivery.message = "";
        }
    }

    private List<TelegramRecipient> allRecipients() {
        return em.createQuery("select r from TelegramRecipient r order by r.id", TelegramRecipient.class).getResultList();
    }

    private boolean hasRecipient(long chatId) {
        return em.createQuery("select count(r) from TelegramRecipient r where r.chatId = :chatId", Long.class)
                .setParameter("chatId", chatId).getSingleResult() > 0;
    }

    private Recipient view(TelegramRecipient entity) {
        return new Recipient(entity.id, entity.chatId.toString(), entity.label);
    }
}
