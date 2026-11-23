package uz.osonmarket.auth.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Component;
import uz.osonmarket.domain.identity.User;
import uz.osonmarket.domain.identity.UserProfile;
import uz.osonmarket.domain.reference.Language;

@Component
public class AccountProfiles {
    private final EntityManager em;

    public AccountProfiles(EntityManager em) {
        this.em = em;
    }

    public User create(String email, String username, String passwordHash, String googleSubject,
                       String firstName, String lastName, String language, String role) {
        Instant now = Instant.now();
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setGoogleSubject(googleSubject);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCredentialVersion(0);
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setPreferredLanguage(em.getReference(Language.class, language));
        em.persist(user);

        populateProfile(user, firstName, lastName);
        return user;
    }

    public void ensureProfile(User user, String firstName, String lastName) {
        if (em.find(UserProfile.class, user.getId()) == null) {
            populateProfile(user, firstName, lastName);
        }
    }

    private void populateProfile(User user, String firstName, String lastName) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(limitName(firstName));
        profile.setLastName(limitName(lastName));
        em.persist(profile);
    }

    private String limitName(String name) {
        if (name == null) {
            return null;
        }
        return name.codePointCount(0, name.length()) > 100
                ? name.substring(0, name.offsetByCodePoints(0, 100)) : name;
    }
}
