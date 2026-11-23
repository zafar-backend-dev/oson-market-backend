package uz.osonmarket.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uz.osonmarket.auth.api.AuthException;

@Component
public class GoogleLoginExchange {
    private static final String PENDING = GoogleLoginExchange.class.getName();
    private final SecureRandom random = new SecureRandom();
    private final URI callback;
    private final String origin;

    public GoogleLoginExchange(@Value("${app.google.frontend-callback}") String callback,
                               @Value("${app.cors-origin}") String origin) {
        this.callback = URI.create(callback);
        this.origin = origin;
        if (!origin.equals(this.callback.getScheme() + "://" + this.callback.getRawAuthority())
                || this.callback.getHost() == null || this.callback.getUserInfo() != null
                || this.callback.getRawQuery() != null || this.callback.getRawFragment() != null
                || !("https".equals(this.callback.getScheme()) || "http".equals(this.callback.getScheme()))) {
            throw new IllegalArgumentException("Google callback must belong to app.cors-origin");
        }
    }

    public void success(HttpServletRequest request, HttpServletResponse response, AccessTokens.Token token)
            throws IOException {
        invalidate(request);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.getSession(true).setAttribute(PENDING, new Pending(code, Instant.now().plusSeconds(60),
                new AtomicReference<>(token)));
        redirect(response, "loginCode", code);
    }

    public void failure(HttpServletRequest request, HttpServletResponse response, String code) throws IOException {
        invalidate(request);
        redirect(response, "error", code);
    }

    public AccessTokens.Token redeem(HttpServletRequest request, String code) {
        if (!origin.equals(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new AuthException(HttpStatus.FORBIDDEN, "auth.forbidden");
        }
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING) instanceof Pending pending)
                || !pending.expiresAt().isAfter(Instant.now()) || !pending.code().equals(code)) {
            throw invalid();
        }
        AccessTokens.Token token = pending.token().getAndSet(null);
        if (token == null) {
            throw invalid();
        }
        return token;
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "auth.google.exchange.invalid");
    }

    private void invalidate(HttpServletRequest request) {
        var existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
    }

    private void redirect(HttpServletResponse response, String key, String value) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect(UriComponentsBuilder.fromUri(callback).queryParam(key, value)
                .build().encode().toUriString());
    }

    private record Pending(String code, Instant expiresAt, AtomicReference<AccessTokens.Token> token) {}
}
