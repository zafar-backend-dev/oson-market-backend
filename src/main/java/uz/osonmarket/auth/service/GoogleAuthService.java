package uz.osonmarket.auth.service;

import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.api.AuthMessages;
import uz.osonmarket.auth.persistence.AccountProfiles;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.auth.persistence.UserRepository;
import uz.osonmarket.domain.identity.User;

@Service
public class GoogleAuthService {
    private final UserRepository users;
    private final AccountProfiles profiles;
    private final AuthLocks locks;
    private final PasswordEncoder passwords;
    private final SessionTokens tokens;

    public GoogleAuthService(UserRepository users, AccountProfiles profiles, AuthLocks locks,
                             PasswordEncoder passwords, SessionTokens tokens) {
        this.users = users;
        this.profiles = profiles;
        this.locks = locks;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public AccessTokens.Token signIn(OidcUser identity, Locale locale) {
        if (!Boolean.TRUE.equals(identity.getEmailVerified()) || identity.getEmail() == null
                || identity.getSubject() == null || identity.getEmail().length() > 255) {
            throw new AuthException(HttpStatus.FORBIDDEN, "auth.google.unverified");
        }
        String email = PasswordAuthService.normalize(identity.getEmail());
        String subject = identity.getSubject();
        locks.lock("google:" + subject);
        locks.lock("account:" + email);

        User linked = users.findByGoogleSubject(subject).orElse(null);
        if (linked != null) {
            return signInActive(linked, identity);
        }
        User existing = users.findByEmailForUpdate(email).orElse(null);
        if (existing != null) {
            if (!PasswordAuthService.active(existing) || existing.getGoogleSubject() != null
                    || !"USER".equals(existing.getRole()) || !authoritativeEmail(identity, email)) {
                throw new AuthException(HttpStatus.CONFLICT, "auth.google.conflict");
            }
            existing.setGoogleSubject(subject);
            existing.setUpdatedAt(java.time.Instant.now());
            return signInActive(existing, identity);
        }
        String username = "google_" + UUID.randomUUID().toString().replace("-", "");
        locks.lock("username:" + username);
        User created = profiles.create(email, username, passwords.encode(UUID.randomUUID().toString()), subject,
                identity.getGivenName(), identity.getFamilyName(), AuthMessages.language(locale), "USER");
        return tokens.issue(created);
    }

    private boolean authoritativeEmail(OidcUser identity, String email) {
        String hostedDomain = identity.getClaimAsString("hd");
        return email.endsWith("@gmail.com") || (hostedDomain != null && !hostedDomain.isBlank());
    }

    private AccessTokens.Token signInActive(User user, OidcUser identity) {
        if (!PasswordAuthService.active(user)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "auth.credentials");
        }
        profiles.ensureProfile(user, identity.getGivenName(), identity.getFamilyName());
        return tokens.issue(user);
    }
}
