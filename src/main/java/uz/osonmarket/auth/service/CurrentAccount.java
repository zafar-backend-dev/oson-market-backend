package uz.osonmarket.auth.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.domain.identity.User;
import uz.osonmarket.domain.identity.UserProfile;

@Service
public class CurrentAccount {
    private final EntityManager em;

    public CurrentAccount(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    public View get(UUID id) {
        User user = em.find(User.class, id);
        if (!PasswordAuthService.active(user)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "auth.unauthorized");
        }
        UserProfile profile = em.find(UserProfile.class, id);
        String languageCode = user.getPreferredLanguage() == null ? "uz" : user.getPreferredLanguage().getCode();
        return new View(id, user.getEmail(), user.getUsername(), user.getRole(), languageCode,
                profile == null ? null : profile.getFirstName(), profile == null ? null : profile.getLastName());
    }

    public record View(UUID id, String email, String username, String role, String language,
                       String firstName, String lastName) {}
}
