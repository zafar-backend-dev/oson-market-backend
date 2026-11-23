package uz.osonmarket.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import uz.osonmarket.domain.identity.User;

@Component
public class AccessTokens {
    public static final String ISSUER = "oson-market";
    private final JwtEncoder encoder;
    private final long ttl;

    public AccessTokens(JwtEncoder encoder, @Value("${jwt.ttl-seconds}") long ttl) {
        this.encoder = encoder;
        this.ttl = ttl;
    }

    public Token issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject(user.getId().toString())
                .id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .claim("ver", user.getCredentialVersion()).build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(value, "Bearer", ttl);
    }

    public record Token(String accessToken, String tokenType, long expiresIn, @JsonIgnore String refreshSecret) {
        public Token(String accessToken, String tokenType, long expiresIn) {
            this(accessToken, tokenType, expiresIn, null);
        }
    }
}
