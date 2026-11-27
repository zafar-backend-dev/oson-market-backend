package uz.osonmarket.profile;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.domain.identity.AccountDeletionRequest;

@RestController
@RequestMapping("/api/admin/account-deletion-requests")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountDeletionController {
    private final EntityManager em;

    public AdminAccountDeletionController(EntityManager em) {
        this.em = em;
    }

    @GetMapping
    public ResponseEntity<List<View>> list(@RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > Integer.MAX_VALUE / 50) {
            return ResponseEntity.badRequest().build();
        }
        var requests = em.createQuery(
                "select r from AccountDeletionRequest r order by r.verifiedAt desc, r.accountId", AccountDeletionRequest.class)
                .setFirstResult(page * 50).setMaxResults(50).getResultList().stream()
                .map(r -> new View(r.getAccountId(), r.getVerifiedAt(), "PENDING")).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(requests);
    }
    public record View(UUID accountId, Instant verifiedAt, String status) {}
}
