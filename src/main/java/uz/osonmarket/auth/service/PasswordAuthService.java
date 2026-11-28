package uz.osonmarket.auth.service;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.api.AuthMessages;
import uz.osonmarket.auth.config.AuthProperties;
import uz.osonmarket.auth.mail.BrevoMailer;
import uz.osonmarket.auth.persistence.*;
import uz.osonmarket.domain.identity.AccountDeletionRequest;
import uz.osonmarket.domain.identity.AuthVerificationCode;
import uz.osonmarket.domain.identity.User;

@Service
public class PasswordAuthService {
    private static final String SIGN_IN = "sign-in";
    private static final String SIGN_UP = "sign-up";
    private static final String RESET = "reset";
    private static final String CHANGE_EMAIL = "change-email";
    private static final String DELETE_ACCOUNT = "delete-account";
    private static final String BACKUP_ADMIN = "backup-admin";

    @Transactional
    public UUID requestBackupAdminCode(UUID userId, Locale locale) {
        User user = users.findById(userId).orElseThrow(this::unauthorized);
        if (!active(user) || !"ADMIN".equals(user.getRole())) throw unauthorized();
        return issue(user.getEmail(), BACKUP_ADMIN, user, null, locale);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void verifyBackupAdminCode(UUID userId, UUID id, String code) {
        AuthVerificationCode challenge = verify(id, code, BACKUP_ADMIN, userId);
        if (!"ADMIN".equals(currentUser(challenge).getRole())) throw unauthorized();
        consume(challenge);
    }

    private static final String PUBLIC_DELETE = "public-delete-account";
    private final EntityManager em;
    private final uz.osonmarket.recovery.DeletionLedger ledger;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final uz.osonmarket.order.OrderOperationsService orderOperations;
    private final uz.osonmarket.auth.mail.DeletionCodeDelivery deletionDelivery;
    private final UserRepository users;
    private final AccountProfiles profiles;
    private final ChallengeRepository challenges;
    private final AuthLocks locks;
    private final CredentialRevocation credentials;
    private final BrevoMailer mail;
    private final PasswordEncoder passwords;
    private final AuthProperties settings;
    private final SessionTokens tokens;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public PasswordAuthService(UserRepository users, AccountProfiles profiles, ChallengeRepository challenges,
                               AuthLocks locks, CredentialRevocation credentials, BrevoMailer mail, PasswordEncoder passwords,
                               AuthProperties settings, SessionTokens tokens, EntityManager em,
                               uz.osonmarket.auth.mail.DeletionCodeDelivery deletionDelivery,
                               org.springframework.jdbc.core.JdbcTemplate db, org.springframework.context.ApplicationEventPublisher events,
                               uz.osonmarket.order.OrderOperationsService orderOperations,
                               uz.osonmarket.recovery.DeletionLedger ledger) {
        this.db = db;
        this.ledger = ledger;
        this.events = events;
        this.orderOperations = orderOperations;
        this.deletionDelivery = deletionDelivery;
        this.em = em;
        this.users = users;
        this.profiles = profiles;
        this.challenges = challenges;
        this.locks = locks;
        this.credentials = credentials;
        this.mail = mail;
        this.passwords = passwords;
        this.settings = settings;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UUID signIn(String login, String password, Locale locale) {
        validatePasswordLength(password);
        String identifier = normalize(login);
        User user = (identifier.contains("@") ? users.findByEmail(identifier) : users.findByUsername(identifier))
                .orElse(null);
        boolean validPassword = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (!validPassword || !active(user) || ledger.contains(user.getId())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "auth.credentials");
        }
        return issue(user.getEmail(), SIGN_IN, user, null, locale);
    }

    @Transactional
    public UUID signUp(String email, String username, String password, String firstName, String lastName,
                       Locale locale) {
        validatePasswordLength(password);
        email = normalize(email);
        username = normalize(username);
        ensureAvailable(email, username);
        var registration = new PendingRegistration(username, passwords.encode(password), firstName.strip(), lastName.strip());
        return issue(email, SIGN_UP, null, registration, locale);
    }

    @Transactional
    public UUID forgotPassword(String email, Locale locale) {
        String normalized = normalize(email);
        User user = users.findByEmail(normalized).orElse(null);
        return issue(normalized, RESET, active(user) ? user : null, null, locale);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AccessTokens.Token verifySignIn(UUID id, String code) {
        AuthVerificationCode challenge = verify(id, code, SIGN_IN);
        User user = currentUser(challenge);
        consume(challenge);
        return tokens.issue(user);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AccessTokens.Token verifySignUp(UUID id, String code, Locale locale) {
        AuthVerificationCode challenge = verify(id, code, SIGN_UP);
        locks.lock("account:" + challenge.getEmail());
        if (challenge.getPendingFirstName() == null || challenge.getPendingLastName() == null) {
            consume(challenge);
            throw new AuthException(HttpStatus.BAD_REQUEST, "auth.signup.restart");
        }
        locks.lock("username:" + challenge.getPendingUsername());
        ensureAvailable(challenge.getEmail(), challenge.getPendingUsername());
        User user = profiles.create(challenge.getEmail(), challenge.getPendingUsername(),
                challenge.getPendingPasswordHash(), null, challenge.getPendingFirstName(),
                challenge.getPendingLastName(), AuthMessages.language(locale), "USER");
        consume(challenge);
        return tokens.issue(user);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void resetPassword(UUID id, String code, String newPassword) {
        validatePasswordLength(newPassword);
        AuthVerificationCode challenge = verify(id, code, RESET);
        User user = currentUser(challenge);
        user.setPasswordHash(passwords.encode(newPassword));
        credentials.revoke(user);
        consume(challenge);
    }

    @Transactional
    public UUID changeEmail(UUID userId, int version, String email, String currentPassword, Locale locale) {
        validatePasswordLength(currentPassword);
        User user = users.findById(userId).orElseThrow(this::unauthorized);
        if (!active(user) || !Objects.equals(user.getCredentialVersion(), version)) {
            throw unauthorized();
        }
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "profile.password.invalid");
        }
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new AuthException(HttpStatus.CONFLICT, "profile.email.taken");
        }
        return issue(normalized, CHANGE_EMAIL, user, null, locale);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void verifyEmailChange(UUID userId, int version, UUID id, String code) {
        AuthVerificationCode challenge = verify(id, code, CHANGE_EMAIL, userId);
        locks.lock("account:" + challenge.getEmail());
        User user = currentUser(challenge);
        if (!Objects.equals(user.getCredentialVersion(), version)) {
            throw unauthorized();
        }
        if (users.existsByEmail(challenge.getEmail())) {
            throw new AuthException(HttpStatus.CONFLICT, "profile.email.taken");
        }
        Instant now = Instant.now();
        user.setEmail(challenge.getEmail());
        user.setEmailVerifiedAt(now);
        credentials.revoke(user);
        consume(challenge);
    }

    @Transactional
    public UUID requestAccountDeletion(UUID userId, Locale locale) {
        User user = users.findById(userId).orElseThrow(this::unauthorized);
        if (!active(user) || !"USER".equals(user.getRole())) throw unauthorized();
        return issue(user.getEmail(), DELETE_ACCOUNT, user, null, locale);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UUID verifyAccountDeletion(UUID userId, UUID id, String code) {
        return queueDeletion(verify(id, code, DELETE_ACCOUNT, userId));
    }

    @Transactional
    public UUID requestPublicAccountDeletion(String rawEmail, Locale locale) {
        String email = normalize(rawEmail);
        locks.lock("public-account-deletion:rate");
        long recent = em.createQuery("select count(c) from AuthVerificationCode c where c.purpose = :purpose and c.issuedAt > :since", Long.class)
                .setParameter("purpose", PUBLIC_DELETE).setParameter("since", Instant.now().minusSeconds(60)).getSingleResult();
        if (recent >= 30) throw AuthException.cooldown(60);
        User candidate = users.findByEmail(email).orElse(null);
        User owner = active(candidate) && "USER".equals(candidate.getRole()) ? candidate : null;
        return issue(email, PUBLIC_DELETE, owner, null, locale);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UUID verifyPublicAccountDeletion(UUID id, String code) {
        return queueDeletion(verify(id, code, PUBLIC_DELETE));
    }

    private UUID queueDeletion(AuthVerificationCode challenge) {
        User owner = currentUser(challenge);
        if (!"USER".equals(owner.getRole())) throw invalidCode();
        ledger.record(owner.getId());
        var request = em.find(AccountDeletionRequest.class, owner.getId());
        if (request == null) {
            request = new AccountDeletionRequest(owner.getId(), Instant.now());
            em.persist(request);
        }
        db.update("INSERT INTO security_retention_events(event_type) VALUES ('ACCOUNT_ERASURE_CONFIRMED')");
        owner.setStatus("DELETION_PENDING");
        credentials.revoke(owner);
        orderOperations.cancelUnpaidForDeletion(owner.getId());
        db.update("DELETE FROM order_push_devices WHERE user_id = ?", owner.getId());
        events.publishEvent(new uz.osonmarket.profile.AccountRemoved(owner.getId()));
        consume(challenge);
        return request.ensureReceipt();
    }

    private AuthException unauthorized() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "auth.unauthorized");
    }

    private UUID issue(String email, String purpose, User user, PendingRegistration registration, Locale locale) {
        locks.lock("challenge:" + purpose + ":" + email);
        Instant now = Instant.now();
        long resendSeconds = (DELETE_ACCOUNT.equals(purpose) || PUBLIC_DELETE.equals(purpose) || BACKUP_ADMIN.equals(purpose)) ? 60 : settings.resendSeconds();
        long ttlSeconds = (DELETE_ACCOUNT.equals(purpose) || PUBLIC_DELETE.equals(purpose) || BACKUP_ADMIN.equals(purpose)) ? 300 : settings.codeTtlSeconds();
        AuthVerificationCode prior = challenges.findFirstByEmailAndPurposeAndIssuedAtIsNotNullOrderByIssuedAtDesc(email, purpose)
                .orElse(null);
        if (prior != null) {
            Instant retryAt = prior.getIssuedAt().plusSeconds(resendSeconds);
            if (retryAt.isAfter(now)) {
                if (RESET.equals(purpose)) {
                    return prior.getId();
                }
                Duration remaining = Duration.between(now, retryAt);
                long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
                throw AuthException.cooldown(seconds);
            }
            challenges.findForUpdate(prior.getId()).filter(c -> c.getConsumedAt() == null).ifPresent(this::consume);
        }
        AuthVerificationCode challenge = new AuthVerificationCode();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        challenge.setEmail(email);
        challenge.setUser(user);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(passwords.encode(code));
        challenge.setAttempts(0);
        challenge.setIssuedAt(now);
        challenge.setExpiresAt(now.plusSeconds(ttlSeconds));
        if (registration != null) {
            challenge.setPendingUsername(registration.username());
            challenge.setPendingPasswordHash(registration.passwordHash());
            challenge.setPendingFirstName(registration.firstName());
            challenge.setPendingLastName(registration.lastName());
        }
        if (user != null) {
            challenge.setCredentialVersion(user.getCredentialVersion());
        }
        challenges.saveAndFlush(challenge);
        if (PUBLIC_DELETE.equals(purpose)) {
            if (user != null) deletionDelivery.afterCommit(email, code, locale);
        } else if (!RESET.equals(purpose) || user != null) {
            mail.sendCode(email, purpose, code, ttlSeconds, locale);
        }
        return challenge.getId();
    }

    private AuthVerificationCode verify(UUID id, String code, String purpose) {
        return verify(id, code, purpose, null);
    }

    private AuthVerificationCode verify(UUID id, String code, String purpose, UUID owner) {
        AuthVerificationCode challenge = challenges.findForUpdate(id).orElseThrow(this::invalidCode);
        if (owner != null && (challenge.getUser() == null || !owner.equals(challenge.getUser().getId()))) {
            throw invalidCode();
        }
        if (!purpose.equals(challenge.getPurpose()) || challenge.getConsumedAt() != null
                || challenge.getIssuedAt() == null || !challenge.getExpiresAt().isAfter(Instant.now())
                || challenge.getAttempts() >= settings.maxAttempts()) {
            throw invalidCode();
        }
        if (!passwords.matches(code, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= settings.maxAttempts()) {
                consume(challenge);
            }
            throw invalidCode();
        }
        return challenge;
    }

    private User currentUser(AuthVerificationCode challenge) {
        if (challenge.getUser() == null) {
            throw invalidCode();
        }
        User user = users.findForUpdate(challenge.getUser().getId()).orElseThrow(this::invalidCode);
        if (!active(user) || !Objects.equals(user.getCredentialVersion(), challenge.getCredentialVersion())) {
            throw invalidCode();
        }
        return user;
    }

    private void consume(AuthVerificationCode challenge) {
        challenge.setConsumedAt(Instant.now());
        challenge.setPendingPasswordHash(null);
        challenge.setPendingFirstName(null);
        challenge.setPendingLastName(null);
    }

    private void ensureAvailable(String email, String username) {
        if (users.existsByEmail(email) || users.existsByUsername(username)) {
            throw new AuthException(HttpStatus.CONFLICT, "auth.account.exists");
        }
    }

    static boolean active(User user) {
        return user != null && "ACTIVE".equals(user.getStatus()) && user.getEmailVerifiedAt() != null;
    }

    static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "auth.password.length");
        }
    }

    private AuthException invalidCode() {
        return new AuthException(HttpStatus.BAD_REQUEST, "auth.code.invalid");
    }

    private record PendingRegistration(String username, String passwordHash, String firstName, String lastName) {}
}
