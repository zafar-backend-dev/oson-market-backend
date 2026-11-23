package uz.osonmarket.order;

import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import uz.osonmarket.auth.mail.BrevoMailer;

@Component
class OrderStatusEmailWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderStatusEmailWorker.class);
    private final OrderStatusEmailRepository emails;
    private final BrevoMailer mail;
    private final MessageSource messages;
    private final TransactionTemplate transactions;

    OrderStatusEmailWorker(OrderStatusEmailRepository emails, BrevoMailer mail, MessageSource messages,
                           TransactionTemplate transactions) {
        this.emails = emails;
        this.mail = mail;
        this.messages = messages;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${app.order-email.interval-ms:15000}",
               initialDelayString = "${app.order-email.interval-ms:15000}")
    void dispatch() {
        for (var row : emails.findTop20ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant.now())) {
            transactions.executeWithoutResult(ignored -> emails.lock(row.id).ifPresent(this::deliver));
        }
    }

    private void deliver(OrderStatusEmail row) {
        Instant now = Instant.now();
        if (row.nextAttemptAt == null || row.nextAttemptAt.isAfter(now)) return;
        Locale locale = Locale.forLanguageTag(row.language);
        boolean rating = row.status == OrderStatus.DELIVERED;
        String subject = messages.getMessage(rating ? "order.rating.title" : "order.notification.title", null, locale);
        String status = messages.getMessage("order.status." + row.status.name(), null, locale);
        String text = messages.getMessage(rating ? "order.rating.message" : "order.notification.message",
                new Object[]{row.orderNumber, status}, locale);
        String reason = row.reason == null ? "" : "<p>" + HtmlUtils.htmlEscape(
                messages.getMessage("order.status.reason", new Object[]{row.reason}, locale)) + "</p>";
        String html = """
                <!doctype html><html lang="%s"><body><main><h1>Oson Market</h1><h2>%s</h2><p>%s</p>%s</main></body></html>
                """.formatted(HtmlUtils.htmlEscape(locale.getLanguage()), HtmlUtils.htmlEscape(subject),
                HtmlUtils.htmlEscape(text), reason);
        try {
            mail.sendHtml(row.recipient, subject, html, row.id);
            row.sentAt = now;
            row.nextAttemptAt = null;
            row.recipient = null;
            row.reason = null;
        } catch (RestClientException error) {
            row.attempts++;
            row.nextAttemptAt = row.attempts >= 5 ? null : now.plusSeconds(60L << (row.attempts - 1));
            if (row.nextAttemptAt == null) {
                row.recipient = null;
                row.reason = null;
                log.warn("Order status email delivery exhausted for job {}", row.id);
            }
        }
    }
}
