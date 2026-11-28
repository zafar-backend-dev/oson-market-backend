package uz.osonmarket.retention;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true")
public class RetentionScheduler {
    // Reserved PostgreSQL advisory lock for this scheduler, shared by all instances.
    private static final long LOCK_ID = 638904723170L;
    private static final Logger LOG = LoggerFactory.getLogger(RetentionScheduler.class);

    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final boolean dryRun;
    private final int batchSize;
    private final Duration otpGrace;
    private final Duration tokenGrace;
    private final Duration securityAge;
    private volatile boolean ready;

    public RetentionScheduler(JdbcTemplate db, TransactionTemplate transactions,
                              @Value("${app.retention.dry-run:true}") boolean dryRun,
                              @Value("${app.retention.batch-size:200}") int batchSize,
                              @Value("${app.retention.otp-grace:PT1H}") Duration otpGrace,
                              @Value("${app.retention.refresh-grace:P7D}") Duration tokenGrace,
                              @Value("${app.retention.security-age:P90D}") Duration securityAge) {
        if (batchSize < 1 || batchSize > 1000 || otpGrace.compareTo(Duration.ofHours(1)) < 0
                || tokenGrace.isNegative() || securityAge.isZero() || securityAge.isNegative()) {
            throw new IllegalArgumentException("Invalid retention limits");
        }
        this.db = db;
        this.transactions = transactions;
        this.dryRun = dryRun;
        this.batchSize = batchSize;
        this.otpGrace = otpGrace;
        this.tokenGrace = tokenGrace;
        this.securityAge = securityAge;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ready() {
        ready = true;
    }

    @Scheduled(fixedDelayString = "${app.retention.interval-ms:300000}")
    void cleanup() {
        if (!ready) return;
        try {
            Map<String, Integer> counts = transactions.execute(status -> {
                if (!Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(?)",
                        Boolean.class, LOCK_ID))) return null;
                var now = db.queryForObject("SELECT now()", OffsetDateTime.class);
                Map<String, Integer> result = new LinkedHashMap<>();
                result.put("otp", batch("auth_verification_codes", "expires_at < ?", "expires_at", now.minus(otpGrace)));
                result.put("refresh", batch("refresh_tokens", "LEAST(expires_at, revoked_at) < ?",
                        "LEAST(expires_at, revoked_at)", now.minus(tokenGrace)));
                result.put("security", batch("security_retention_events", "occurred_at < ? AND NOT retention_hold",
                        "occurred_at", now.minus(securityAge)));
                return result;
            });
            if (counts != null) LOG.info("Retention batch dryRun={} counts={}", dryRun, counts);
        } catch (RuntimeException failure) {
            LOG.warn("Retention batch rolled back and will retry ({})", failure.getClass().getSimpleName());
        }
    }

    private int batch(String table, String condition, String chronology, OffsetDateTime cutoff) {
        // SQL fragments are fixed by the caller. Dry run counts at most one configured batch.
        String eligible = "SELECT id FROM " + table + " WHERE " + condition
                + " ORDER BY " + chronology + ", id LIMIT ?";
        if (dryRun) return db.queryForObject("SELECT count(*) FROM (" + eligible + ") eligible",
                Integer.class, cutoff, batchSize);
        return db.update("DELETE FROM " + table + " WHERE id IN (" + eligible + " FOR UPDATE SKIP LOCKED)",
                cutoff, batchSize);
    }
}
