package uz.osonmarket.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
    private final URI mobileCallback;
    private final Map<String, MobilePending> mobileCodes = new HashMap<>();
    private static final String MOBILE = PENDING + ".mobile";

    public GoogleLoginExchange(@Value("${app.google.frontend-callback}") String callback,
                               @Value("${app.cors-origin}") String origin,
                               @Value("${app.google.mobile-callback}") URI mobileCallback) {
        this.callback = URI.create(callback);
        this.origin = origin;
        this.mobileCallback = mobileCallback;
        if (!"https".equals(mobileCallback.getScheme())
                || !origin.equals(mobileCallback.getScheme() + "://" + mobileCallback.getRawAuthority())
                || mobileCallback.getUserInfo() != null || mobileCallback.getRawQuery() != null
                || mobileCallback.getRawFragment() != null) {
            throw new IllegalArgumentException("Mobile Google callback must use the trusted HTTPS origin");
        }
        if (!origin.equals(this.callback.getScheme() + "://" + this.callback.getRawAuthority())
                || this.callback.getHost() == null || this.callback.getUserInfo() != null
                || this.callback.getRawQuery() != null || this.callback.getRawFragment() != null
                || !("https".equals(this.callback.getScheme()) || "http".equals(this.callback.getScheme()))) {
            throw new IllegalArgumentException("Google callback must belong to app.cors-origin");
        }
    }

    public void success(HttpServletRequest request, HttpServletResponse response, AccessTokens.Token token)
            throws IOException {
        String challenge = mobileChallenge(request);
        invalidate(request);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (challenge != null) {
            storeMobile(code, challenge, token);
            redirect(response, mobileCallback, "loginCode", code);
            return;
        }
        request.getSession(true).setAttribute(PENDING, new Pending(code, Instant.now().plusSeconds(60),
                new AtomicReference<>(token)));
        redirect(response, callback, "loginCode", code);
    }

    public void failure(HttpServletRequest request, HttpServletResponse response, String code) throws IOException {
        URI target = mobileChallenge(request) == null ? callback : mobileCallback;
        invalidate(request);
        redirect(response, target, "error", code);
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

    public void bindMobileRequest(HttpServletRequest request, String state, String challenge) {
        if (challenge == null) {
            var session = request.getSession(false);
            if (session != null) session.removeAttribute(MOBILE);
            return;
        }
        if (!challenge.matches("[A-Za-z0-9_-]{43}")) {
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_request");
        }
        request.getSession(true).setAttribute(MOBILE, new MobileRequest(state, challenge));
    }

    private String mobileChallenge(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(MOBILE) instanceof MobileRequest mobile
                && mobile.state().equals(request.getParameter("state"))) {
            return mobile.challenge();
        }
        return null;
    }

    private synchronized void storeMobile(String code, String challenge, AccessTokens.Token token) {
        var now = Instant.now();
        mobileCodes.values().removeIf(value -> !value.expiresAt().isAfter(now));
        if (mobileCodes.size() >= 4096) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "auth.google.failed");
        }
        mobileCodes.put(code, new MobilePending(challenge, now.plusSeconds(60), token));
    }

    public synchronized AccessTokens.Token redeemMobile(String code, String verifier) {
        var stored = mobileCodes.get(code);
        if (stored == null || !stored.expiresAt().isAfter(Instant.now())) {
            mobileCodes.remove(code);
            throw invalid();
        }
        if (!MessageDigest.isEqual(stored.challenge().getBytes(StandardCharsets.US_ASCII),
                challenge(verifier).getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        mobileCodes.remove(code);
        return stored.token();
    }

    private String challenge(String verifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record MobileRequest(String state, String challenge) {}
    private record MobilePending(String challenge, Instant expiresAt, AccessTokens.Token token) {}

    private AuthException invalid() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "auth.google.exchange.invalid");
    }

    private void invalidate(HttpServletRequest request) {
        var existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
    }

    private void redirect(HttpServletResponse response, URI destination, String key, String value) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.sendRedirect(UriComponentsBuilder.fromUri(destination).queryParam(key, value)
                .build().encode().toUriString());
    }

    private record Pending(String code, Instant expiresAt, AtomicReference<AccessTokens.Token> token) {}
}
