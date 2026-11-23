package uz.osonmarket.auth.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Component;
import uz.osonmarket.domain.identity.User;

@Component
public class CredentialRevocation {
    private final EntityManager em;

    public CredentialRevocation(EntityManager em) {
        this.em = em;
    }

    public void revoke(User user) {
        Instant now = Instant.now();
        user.setCredentialVersion(user.getCredentialVersion() + 1);
        user.setUpdatedAt(now);
        em.createQuery("update RefreshToken r set r.revokedAt = :now where r.user.id = :id and r.revokedAt is null")
                .setParameter("now", now).setParameter("id", user.getId()).executeUpdate();
    }
}
