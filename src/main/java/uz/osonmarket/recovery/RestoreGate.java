package uz.osonmarket.recovery;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.osonmarket.profile.AccountDeletionService;

@Component
@Order(100)
public class RestoreGate extends OncePerRequestFilter implements ApplicationRunner {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final DeletionLedger ledger;
    private final AccountDeletionService deletion;
    private final String checkpoint;
    private volatile boolean ready;

    public RestoreGate(JdbcTemplate db, TransactionTemplate transactions, DeletionLedger ledger,
                       AccountDeletionService deletion,
                       @Value("${app.deletion-ledger.restore-sha256:}") String checkpoint) {
        this.db = db;
        this.transactions = transactions;
        this.ledger = ledger;
        this.deletion = deletion;
        this.checkpoint = checkpoint;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String required = db.queryForObject("SELECT current_setting('osonmarket.restore_required', true)", String.class);
        if ("on".equals(required) || !checkpoint.isBlank()) {
            String generation = db.queryForObject("SELECT current_setting('osonmarket.restore_generation', true)", String.class);
            if (generation == null || generation.isBlank()) {
                throw new IllegalStateException("Recovery requires a restore generation marker");
            }
            // Read and validate the entire checkpoint before changing restored data.
            for (UUID id : ledger.readCheckpoint(checkpoint)) {
                if (prepare(id)) {
                    deletion.eraseIdentity(id);
                    deletion.advance(id);
                }
            }
            // Restored sessions, including sessions for non-deleted accounts, require fresh login.
            transactions.executeWithoutResult(status -> {
                db.execute("CREATE TABLE IF NOT EXISTS restore_reconciliation (checkpoint varchar(64) PRIMARY KEY)");
                if (db.update("""
                        INSERT INTO restore_reconciliation VALUES (encode(sha256(convert_to(?, 'UTF8')), 'hex'))
                        ON CONFLICT DO NOTHING
                        """, generation + ":" + checkpoint) == 0) return;
                db.update("DELETE FROM refresh_tokens");
                db.update("UPDATE users SET credential_version = COALESCE(credential_version, 0) + 1");
                db.update("DELETE FROM auth_verification_codes");
            });
        }
        ready = true;
    }

    private boolean prepare(UUID id) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int changed = db.update("UPDATE users SET status = 'DELETION_PENDING' WHERE id = ? AND role = 'USER'", id);
            if (changed == 0) return false;
            db.update("""
                    INSERT INTO account_deletion_requests(account_id, verified_at, status)
                    VALUES (?, now(), 'PENDING')
                    ON CONFLICT (account_id) DO UPDATE SET status = 'PENDING', completed_at = NULL,
                        next_attempt_at = now()
                    """, id);
            db.update("DELETE FROM order_push_devices WHERE user_id = ?", id);
            return true;
        }));
    }

    public boolean isReady() { return ready; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!ready) {
            response.setStatus(503);
            response.setHeader("Cache-Control", "no-store");
            return;
        }
        chain.doFilter(request, response);
    }
}
