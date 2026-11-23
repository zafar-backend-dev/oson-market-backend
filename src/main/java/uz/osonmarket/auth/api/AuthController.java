package uz.osonmarket.auth.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.service.AccessTokens.Token;
import uz.osonmarket.auth.service.CurrentAccount;
import uz.osonmarket.auth.service.PasswordAuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final PasswordAuthService auth;
    private final CurrentAccount accounts;
    private final AuthMessages messages;
    private final SessionController sessions;

    public AuthController(PasswordAuthService auth, CurrentAccount accounts, AuthMessages messages, SessionController sessions) {
        this.auth = auth;
        this.accounts = accounts;
        this.messages = messages;
        this.sessions = sessions;
    }

    @PostMapping("/sign-in")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Challenge> signIn(@Valid @RequestBody AuthRequests.SignIn input, Locale locale) {
        return challenge(auth.signIn(input.login(), input.password(), locale), "auth.code.sent", locale);
    }

    @PostMapping("/sign-in/verify")
    public ResponseEntity<Token> verifySignIn(@Valid @RequestBody AuthRequests.Verify input, Locale locale,
                                             HttpServletRequest request) {
        return login(HttpStatus.OK, auth.verifySignIn(input.challengeId(), input.code()), locale, request);
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Challenge> signUp(@Valid @RequestBody AuthRequests.SignUp input, Locale locale) {
        return challenge(auth.signUp(input.email(), input.username(), input.password(), input.firstName(), input.lastName(), locale), "auth.code.sent", locale);
    }

    @PostMapping("/sign-up/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Token> verifySignUp(@Valid @RequestBody AuthRequests.Verify input, Locale locale,
                                             HttpServletRequest request) {
        return login(HttpStatus.CREATED, auth.verifySignUp(input.challengeId(), input.code(), locale), locale, request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Challenge> forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword input, Locale locale) {
        return challenge(auth.forgotPassword(input.email(), locale), "auth.reset.requested", locale);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Message> resetPassword(@Valid @RequestBody AuthRequests.ResetPassword input, Locale locale) {
        auth.resetPassword(input.challengeId(), input.code(), input.newPassword());
        return response(HttpStatus.OK, new Message(messages.text("auth.reset.done", locale)), locale);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CurrentAccount.View> me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token, Locale locale) {
        return response(HttpStatus.OK, accounts.get(UUID.fromString(token.getSubject())), locale);
    }

    private ResponseEntity<Challenge> challenge(UUID id, String message, Locale locale) {
        return response(HttpStatus.ACCEPTED, new Challenge(id, messages.text(message, locale)), locale);
    }

    private ResponseEntity<Token> login(HttpStatus status, Token token, Locale locale, HttpServletRequest request) {
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header("Content-Language", AuthMessages.language(locale));
        sessions.attach(response, token, request);
        return response.body(token);
    }

    private <T> ResponseEntity<T> response(HttpStatus status, T body, Locale locale) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header("Content-Language", AuthMessages.language(locale)).body(body);
    }

    public record Challenge(UUID challengeId, String message) {}
    public record Message(String message) {}
}
