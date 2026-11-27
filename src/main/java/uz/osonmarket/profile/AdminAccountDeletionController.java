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

    public AdminAccountDeletionController(JdbcTemplate db, AccountDeletionService deletion) {
        this.db = db;
        this.deletion = deletion;
    }

    @PostMapping("/{accountId}/complete")
    public ResponseEntity<Void> complete(@PathVariable UUID accountId, @RequestBody Completion input) {
        try {
            deletion.complete(accountId, input.obligationsSettled());
        } catch (DataAccessException failure) {
            db.update("UPDATE account_deletion_requests SET status = 'FAILED' WHERE account_id = ? AND status <> 'COMPLETED'", accountId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account deletion could not be completed");
        }
        return ResponseEntity.noContent().build();
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
                """, (rs, row) -> new View(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                rs.getString(3)), page * 50);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests);
    }
    public record View(UUID accountId, Instant verifiedAt, String status) {}
}
