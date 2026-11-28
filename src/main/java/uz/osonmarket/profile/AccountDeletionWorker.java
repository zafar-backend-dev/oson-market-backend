package uz.osonmarket.profile;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AccountDeletionWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionWorker.class);
    private final AccountDeletionService deletion;
    private final JdbcTemplate db;

    public AccountDeletionWorker(AccountDeletionService deletion, JdbcTemplate db) {
        this.deletion = deletion;
        this.db = db;
    }

    @TransactionalEventListener
    public void confirmed(AccountRemoved event) {
        attempt(event.accountId());
    }

    @Scheduled(fixedDelayString = "${account-deletion.poll-ms:15000}", initialDelayString = "${account-deletion.poll-ms:15000}")
    public void processDue() {
        for (UUID id : db.queryForList("""
                SELECT account_id FROM account_deletion_requests
                WHERE status <> 'COMPLETED' AND next_attempt_at <= now()
                ORDER BY next_attempt_at, account_id LIMIT 50
                """, UUID.class)) {
            attempt(id);
        }
    }

    boolean process(UUID accountId) {
        deletion.eraseIdentity(accountId);
        return deletion.advance(accountId);
    }

    private void attempt(UUID accountId) {
        try {
            process(accountId);
        } catch (DataAccessException | ResponseStatusException failure) {
            LOG.warn("Account erasure will retry for {} ({})", accountId, failure.getClass().getSimpleName());
            try {
                deletion.retryLater(accountId);
            } catch (DataAccessException retryFailure) {
                LOG.warn("Could not update account erasure retry time for {}", accountId);
            }
        }
    }
}
