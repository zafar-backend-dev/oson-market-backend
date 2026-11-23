package uz.osonmarket.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.service.AccessTokens;
import uz.osonmarket.auth.service.SessionTokens;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    static final String COOKIE = "oson_refresh";
    private final SessionTokens sessions;
    private final long ttl;
    private final String origin;

    public SessionController(SessionTokens sessions, @Value("${jwt.refresh-ttl-seconds}") long ttl,
                             @Value("${app.cors-origin}") String origin) {
        this.sessions = sessions;
        this.ttl = ttl;
        this.origin = origin;
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccessTokens.Token> refresh(@CookieValue(name = COOKIE, required = false) String secret,
                                                       HttpServletRequest request) {
        checkOrigin(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.refresh(secret));
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String secret,
                                       HttpServletRequest request) {
        checkOrigin(request);
        sessions.logout(secret);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie("", 0, request).toString()).build();
    }

    void attach(ResponseEntity.BodyBuilder response, AccessTokens.Token token, HttpServletRequest request) {
        if (token.refreshSecret() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie(token.refreshSecret(), ttl, request).toString());
        }
    }

    private ResponseCookie cookie(String value, long maxAge, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true)
                .secure(request.isSecure() || origin.startsWith("https://")).sameSite("Lax")
                .path("/api/auth").maxAge(Duration.ofSeconds(maxAge)).build();
    }

    private void checkOrigin(HttpServletRequest request) {
        String supplied = request.getHeader(HttpHeaders.ORIGIN);
        if (supplied != null && !origin.equals(supplied)
                && !supplied.equals(request.getScheme() + "://" + request.getHeader(HttpHeaders.HOST))) {
            throw new AuthException(HttpStatus.FORBIDDEN, "auth.forbidden");
        }
    }
}
