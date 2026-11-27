package uz.osonmarket.auth.mail;

import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DeletionCodeDelivery {
    private final BrevoMailer mailer;
    private final ThreadPoolExecutor queue = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(30));

    public DeletionCodeDelivery(BrevoMailer mailer) {
        this.mailer = mailer;
    }

    public void afterCommit(String email, String code, Locale locale) {
        long expiresAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    queue.execute(() -> {
                        long remaining = TimeUnit.NANOSECONDS.toSeconds(expiresAt - System.nanoTime());
                        if (remaining <= 0) return;
                        try {
                            mailer.sendCode(email, "delete-account", code, remaining, locale);
                        } catch (RuntimeException failure) {
                            LoggerFactory.getLogger(DeletionCodeDelivery.class).warn("Public deletion email delivery failed: {}",
                                    failure.getClass().getSimpleName());
                        }
                    });
                } catch (java.util.concurrent.RejectedExecutionException failure) {
                    LoggerFactory.getLogger(DeletionCodeDelivery.class).warn("Public deletion email queue unavailable");
                }
            }
        });
    }

    @PreDestroy
    void shutdown() {
        queue.shutdownNow();
    }
}
