package uz.osonmarket.auth.service;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.domain.identity.RefreshToken;
import uz.osonmarket.domain.identity.User;

@Service
public class SessionTokens {
    private final SecureRandom random = new SecureRandom();
    private final EntityManager em;
    private final AccessTokens access;
    private final long ttl;

    public SessionTokens(EntityManager em, AccessTokens access, @Value("${jwt.refresh-ttl-seconds}") long ttl) {
        this.em = em;
        this.access = access;
        this.ttl = ttl;
    }

    @Transactional
    public AccessTokens.Token issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(hash(secret));
        stored.setExpiresAt(Instant.now().plusSeconds(ttl));
        em.persist(stored);
        var token = access.issue(user);
        return new AccessTokens.Token(token.accessToken(), token.tokenType(), token.expiresIn(), secret);
    }

    @Transactional(readOnly = true)
    public AccessTokens.Token refresh(String secret) {
        if (validFormat(secret)) {
            var stored = em.createQuery("""
                    select r from RefreshToken r join fetch r.user u
                    where r.tokenHash = :hash and r.revokedAt is null and r.expiresAt > :now
                      and u.status = 'ACTIVE' and u.emailVerifiedAt is not null
                    """, RefreshToken.class)
                    .setParameter("hash", hash(secret)).setParameter("now", Instant.now())
                    .getResultList().stream().findFirst();
            if (stored.isPresent()) {
                return access.issue(stored.get().getUser());
            }
        }
        throw new AuthException(HttpStatus.UNAUTHORIZED, "auth.session.expired");
    }

    @Transactional
    public void logout(String secret) {
        if (validFormat(secret)) {
            em.createQuery("""
                    update RefreshToken r set r.revokedAt = :now
                    where r.tokenHash = :hash and r.revokedAt is null
                    """).setParameter("hash", hash(secret)).setParameter("now", Instant.now()).executeUpdate();
        }
    }

    private static boolean validFormat(String secret) {
        return secret != null && secret.matches("[A-Za-z0-9_-]{43}");
    }

    private static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
