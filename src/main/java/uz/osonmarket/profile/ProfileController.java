package uz.osonmarket.profile;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.auth.api.AuthMessages;
import uz.osonmarket.auth.service.PasswordAuthService;

@RestController
@RequestMapping("/api/profile")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {
    private final ProfileService profiles;
    private final AvatarImages images;
    private final PasswordAuthService auth;
    private final AuthMessages messages;

    ProfileController(ProfileService profiles, AvatarImages images, PasswordAuthService auth, AuthMessages messages) {
        this.profiles = profiles;
        this.images = images;
        this.auth = auth;
        this.messages = messages;
    }

    @GetMapping
    public ResponseEntity<ProfileService.View> get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ok(profiles.get(id(jwt)));
    }

    @PutMapping
    public ResponseEntity<ProfileService.View> update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody ProfileRequests.Update input) {
        return ok(profiles.update(id(jwt), input));
    }

    @PutMapping("/username")
    public ResponseEntity<ProfileService.View> username(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody ProfileRequests.Username input) {
        return ok(profiles.username(id(jwt), input.username()));
    }

    @PutMapping("/language")
    public ResponseEntity<ProfileService.View> language(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody ProfileRequests.Language input) {
        return ok(profiles.language(id(jwt), input.language()));
    }

    @PutMapping("/password")
    public ResponseEntity<Message> password(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody ProfileRequests.Password input, Locale locale) {
        profiles.password(id(jwt), version(jwt), input);
        return message("profile.password.updated", locale);
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Challenge> email(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody ProfileRequests.EmailChange input, Locale locale) {
        UUID id = auth.changeEmail(id(jwt), version(jwt), input.email(), input.currentPassword(), locale);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, AuthMessages.language(locale))
                .body(new Challenge(id, messages.text("profile.email.sent", locale)));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Message> verifyEmail(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody ProfileRequests.VerifyEmail input, Locale locale) {
        auth.verifyEmailChange(id(jwt), version(jwt), input.challengeId(), input.code());
        return message("profile.email.updated", locale);
    }

    @GetMapping(value = "/avatar", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> avatar(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(profiles.avatar(id(jwt)));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileService.View> upload(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                      @RequestPart("file") MultipartFile file) {
        return ok(profiles.avatar(id(jwt), images.readAvatar(file)));
    }

    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        profiles.deleteAvatar(id(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private int version(Jwt jwt) {
        Number version = jwt.getClaim("ver");
        return version.intValue();
    }

    private ResponseEntity<Message> message(String code, Locale locale) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, AuthMessages.language(locale))
                .body(new Message(messages.text(code, locale)));
    }

    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    public record Message(String message) {}
    public record Challenge(UUID challengeId, String message) {}
}
