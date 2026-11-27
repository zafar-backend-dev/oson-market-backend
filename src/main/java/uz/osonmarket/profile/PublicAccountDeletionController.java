package uz.osonmarket.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.service.PasswordAuthService;

@RestController
@RequestMapping("/api/site/account-deletion")
public class PublicAccountDeletionController {
    private final PasswordAuthService auth;
    private final JdbcTemplate db;

    public PublicAccountDeletionController(PasswordAuthService auth, JdbcTemplate db) {
        this.auth = auth;
        this.db = db;
    }

    @PostMapping("/request")
    public ResponseEntity<AccountDeletionController.Challenge> request(@Valid @RequestBody EmailInput input, Locale locale) {
        UUID challenge = auth.requestPublicAccountDeletion(input.email(), locale);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new AccountDeletionController.Challenge(challenge));
    }

    @PostMapping("/verify")
    public ResponseEntity<Receipt> verify(@Valid @RequestBody ProfileRequests.VerifyEmail input) {
        UUID receipt = auth.verifyPublicAccountDeletion(input.challengeId(), input.code());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Receipt(receipt));
    }

    @PostMapping("/status")
    public ResponseEntity<Status> status(@Valid @RequestBody Receipt input) {
        var rows = db.queryForList("SELECT status FROM account_deletion_requests WHERE receipt = ?", String.class, input.receipt());
        if (rows.isEmpty()) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(rows.get(0)));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 900000, initialDelay = 900000)
    void removeOldPublicChallenges() {
        db.update("""
                DELETE FROM auth_verification_codes WHERE purpose = 'public-delete-account'
                    AND issued_at < now() - interval '1 hour'
                """);
    }

    public record EmailInput(@NotBlank @Email @Size(max = 255) String email) {}
    public record Receipt(@NotNull UUID receipt) {}
    public record Status(String status) {}
}
