package uz.osonmarket.profile;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.service.PasswordAuthService;

@RestController
@RequestMapping("/api/users/delete-account")
@SecurityRequirement(name = "bearerAuth")
public class AccountDeletionController {
    private final PasswordAuthService auth;

    public AccountDeletionController(PasswordAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/request")
    public ResponseEntity<Challenge> request(@AuthenticationPrincipal Jwt jwt, Locale locale) {
        UUID id = auth.requestAccountDeletion(UUID.fromString(jwt.getSubject()), locale);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Challenge(id));
    }

    @PostMapping("/verify")
    public ResponseEntity<PublicAccountDeletionController.Receipt> verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequests.VerifyEmail input) {
        UUID receipt = auth.verifyAccountDeletion(UUID.fromString(jwt.getSubject()), input.challengeId(), input.code());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new PublicAccountDeletionController.Receipt(receipt));
    }

    public record Challenge(UUID challengeId) {}
}
