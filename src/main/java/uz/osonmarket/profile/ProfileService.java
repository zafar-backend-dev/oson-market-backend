package uz.osonmarket.profile;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.auth.persistence.UserRepository;
import uz.osonmarket.auth.service.CredentialRevocation;
import uz.osonmarket.domain.identity.User;
import uz.osonmarket.domain.identity.UserProfile;
import uz.osonmarket.domain.media.MediaFile;
import uz.osonmarket.domain.reference.Language;

@Service
public class ProfileService {
    static final String AVATAR_PATH = "/api/profile/avatar";
    private final EntityManager em;
    private final UserRepository users;
    private final AuthLocks locks;
    private final PasswordEncoder passwords;
    private final CredentialRevocation credentials;

    ProfileService(EntityManager em, UserRepository users, AuthLocks locks, PasswordEncoder passwords, CredentialRevocation credentials) {
        this.em = em;
        this.users = users;
        this.locks = locks;
        this.passwords = passwords;
        this.credentials = credentials;
    }

    @Transactional(readOnly = true)
    public View get(UUID id) {
        return view(account(id, false), em.find(UserProfile.class, id));
    }

    @Transactional
    public View update(UUID id, ProfileRequests.Update input) {
        User user = account(id, true);
        UserProfile profile = profile(user);
        profile.setFirstName(clean(input.firstName()));
        profile.setLastName(clean(input.lastName()));
        profile.setPhone(clean(input.phone()));
        user.setUpdatedAt(Instant.now());
        return view(user, profile);
    }

    @Transactional
    public View username(UUID id, String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        locks.lock("username:" + normalized);
        User user = account(id, true);
        if (!normalized.equals(user.getUsername())) {
            if (users.existsByUsername(normalized)) {
                throw new AuthException(HttpStatus.CONFLICT, "profile.username.taken");
            }
            user.setUsername(normalized);
            user.setUpdatedAt(Instant.now());
        }
        return view(user, em.find(UserProfile.class, id));
    }

    @Transactional
    public View language(UUID id, String code) {
        User user = account(id, true);
        user.setPreferredLanguage(em.getReference(Language.class, code));
        user.setUpdatedAt(Instant.now());
        return view(user, em.find(UserProfile.class, id));
    }

    @Transactional
    public void password(UUID id, int version, ProfileRequests.Password input) {
        User user = account(id, true);
        requireVersion(user, version);
        checkPassword(user, input.currentPassword());
        validatePassword(input.newPassword());
        if (passwords.matches(input.newPassword(), user.getPasswordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "profile.password.same");
        }
        user.setPasswordHash(passwords.encode(input.newPassword()));
        credentials.revoke(user);
    }

    @Transactional(readOnly = true)
    public byte[] avatar(UUID id) {
        account(id, false);
        UserProfile profile = em.find(UserProfile.class, id);
        ProfileAvatar avatar = profile == null || profile.getAvatarMedia() == null
                ? null : em.find(ProfileAvatar.class, profile.getAvatarMedia().getId());
        if (avatar == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "profile.avatar.missing");
        }
        return avatar.content();
    }

    @Transactional
    public View avatar(UUID id, AvatarImages.Image image) {
        User user = account(id, true);
        UserProfile profile = profile(user);
        removeAvatar(profile);
        MediaFile media = new MediaFile();
        media.setStorageKey("profile/" + UUID.randomUUID());
        media.setMimeType("image/png");
        media.setSizeBytes((long) image.content().length);
        media.setWidth(image.width());
        media.setHeight(image.height());
        media.setCreatedAt(Instant.now());
        em.persist(media);
        em.persist(new ProfileAvatar(media.getId(), image.content()));
        profile.setAvatarMedia(media);
        user.setUpdatedAt(Instant.now());
        return view(user, profile);
    }

    @Transactional
    public void deleteAvatar(UUID id) {
        User user = account(id, true);
        UserProfile profile = em.find(UserProfile.class, id);
        if (profile != null && profile.getAvatarMedia() != null) {
            removeAvatar(profile);
            user.setUpdatedAt(Instant.now());
        }
    }

    User account(UUID id, boolean lock) {
        User user = lock ? users.findForUpdate(id).orElse(null) : em.find(User.class, id);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || user.getEmailVerifiedAt() == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "auth.unauthorized");
        }
        return user;
    }

    void requireVersion(User user, int version) {
        if (!Objects.equals(user.getCredentialVersion(), version)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "auth.unauthorized");
        }
    }

    void checkPassword(User user, String value) {
        validatePassword(value);
        if (!passwords.matches(value, user.getPasswordHash())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "profile.password.invalid");
        }
    }

    private void validatePassword(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "auth.password.length");
        }
    }

    private void removeAvatar(UserProfile profile) {
        MediaFile media = profile.getAvatarMedia();
        if (media == null) {
            return;
        }
        profile.setAvatarMedia(null);
        ProfileAvatar stored = em.find(ProfileAvatar.class, media.getId());
        if (stored != null) {
            em.remove(stored);
            em.remove(media);
        }
    }

    private UserProfile profile(User user) {
        UserProfile profile = em.find(UserProfile.class, user.getId());
        if (profile == null) {
            profile = new UserProfile();
            profile.setUser(user);
            em.persist(profile);
        }
        return profile;
    }

    private View view(User user, UserProfile profile) {
        var language = user.getPreferredLanguage();
        boolean hasAvatar = profile != null && profile.getAvatarMedia() != null
                && profile.getAvatarMedia().getStorageKey() != null
                && profile.getAvatarMedia().getStorageKey().startsWith("profile/");
        return new View(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                language == null ? "uz" : language.getCode(),
                profile == null ? null : profile.getFirstName(), profile == null ? null : profile.getLastName(),
                profile == null ? null : profile.getPhone(), hasAvatar ? AVATAR_PATH : null,
                user.getEmailVerifiedAt(), user.getCreatedAt(), user.getUpdatedAt());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record View(UUID id, String username, String email, String role, String language,
                       String firstName, String lastName, String phone, String avatarUrl,
                       Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {}
}
