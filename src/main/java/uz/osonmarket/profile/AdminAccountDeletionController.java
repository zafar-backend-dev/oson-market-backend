package uz.osonmarket.profile;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/account-deletion-requests")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountDeletionController {
    private final JdbcTemplate db;
    private final AccountDeletionService deletion;
    private final AccountDeletionWorker worker;

    public AdminAccountDeletionController(JdbcTemplate db, AccountDeletionService deletion, AccountDeletionWorker worker) {
        this.db = db;
        this.deletion = deletion;
        this.worker = worker;
    }

    @PostMapping("/{accountId}/complete")
    public ResponseEntity<Void> complete(@PathVariable UUID accountId, @RequestBody Completion input) {
        try {
            return worker.process(accountId) ? ResponseEntity.noContent().build() : ResponseEntity.accepted().build();
        } catch (DataAccessException failure) {
            deletion.retryLater(accountId);
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    public record Completion(boolean obligationsSettled) {}

    @GetMapping
    public ResponseEntity<List<View>> list(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > Integer.MAX_VALUE / 50) {
            return ResponseEntity.badRequest().build();
        }
        var requests = db.query("""
                SELECT account_id, verified_at, status FROM account_deletion_requests
                ORDER BY verified_at DESC, account_id LIMIT 50 OFFSET ?
                """, (rs, row) -> new View(rs.getObject(1, UUID.class), rs.getObject(2, java.time.OffsetDateTime.class) == null ? null : rs.getObject(2, java.time.OffsetDateTime.class).toInstant(),
                java.util.Objects.requireNonNullElse(rs.getString(3), "PENDING")), page * 50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests);
    }
    public record View(UUID accountId, Instant verifiedAt, String status) {}
}
