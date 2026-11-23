package uz.osonmarket.catalog.admin.shared;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uz.osonmarket.auth.api.AuthException;

@Component
public class CatalogPersistence {
    private final EntityManager em;

    public CatalogPersistence(EntityManager em) {
        this.em = em;
    }

    public Pageable page(Pageable requested) {
        return PageRequest.of(requested.getPageNumber(), Math.min(100, requested.getPageSize()), Sort.by("id"));
    }

    public <T> T optional(Class<T> type, UUID id) {
        return id == null ? null : require(type, id);
    }

    public <T> T require(Class<T> type, Object id) {
        T entity = em.find(type, id);
        if (entity == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
        }
        return entity;
    }

    public UUID id(Object entity) {
        return entity == null ? null : (UUID) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    public AuthException failedConflict() {
        return new AuthException(HttpStatus.CONFLICT, "catalog.conflict");
    }
}
