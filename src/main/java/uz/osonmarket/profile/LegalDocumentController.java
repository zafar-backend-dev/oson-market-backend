package uz.osonmarket.profile;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.auth.api.AuthException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uz.osonmarket.domain.identity.LegalDocumentRevision;

@RestController
public class LegalDocumentController {
    private final EntityManager em;

    private final AuthLocks locks;

    public LegalDocumentController(EntityManager em, AuthLocks locks) {
        this.em = em;
        this.locks = locks;
    }

    @GetMapping("/api/site/legal/{kind}")
    public ResponseEntity<PublicRevision> latest(@PathVariable String kind) {
        requireKind(kind);
        var result = em.createQuery("select d from LegalDocumentRevision d where d.kind = :kind and d.active = true", LegalDocumentRevision.class)
                .setParameter("kind", kind).setMaxResults(1).getResultList();
        return result.isEmpty() ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PublicRevision.from(result.get(0)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/admin/legal/{kind}")
    public ResponseEntity<List<LegalDocumentRevision>> history(@PathVariable String kind) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(revisions(kind, 50));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/admin/legal/{kind}")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public LegalDocumentRevision publish(@PathVariable String kind, @Valid @RequestBody Input input, @AuthenticationPrincipal Jwt jwt) {
        requireKind(kind);
        locks.lock("legal-document:" + kind);
        var existing = em.createQuery("select count(d) from LegalDocumentRevision d where d.kind = :kind and d.version = :version", Long.class)
                .setParameter("kind", kind).setParameter("version", input.version()).getSingleResult();
        if (existing > 0) throw new AuthException(HttpStatus.CONFLICT, "legal.version.exists");
        em.createQuery("update LegalDocumentRevision d set d.active = false where d.kind = :kind and d.active = true")
                .setParameter("kind", kind).executeUpdate();
        var revision = new LegalDocumentRevision(kind, input.version(), input.contentUz(), input.contentRu(), UUID.fromString(jwt.getSubject()));
        em.persist(revision);
        return revision;
    }

    private List<LegalDocumentRevision> revisions(String kind, int limit) {
        requireKind(kind);
        return em.createQuery("select d from LegalDocumentRevision d where d.kind = :kind order by d.publishedAt desc, d.id desc",
                        LegalDocumentRevision.class)
                .setParameter("kind", kind).setMaxResults(limit).getResultList();
    }

    private static void requireKind(String kind) {
        if (!"privacy".equals(kind) && !"terms".equals(kind) && !"delete".equals(kind)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public record PublicRevision(UUID id, String kind, String version, String contentUz, String contentRu,
                                 java.time.Instant publishedAt, boolean active) {
        static PublicRevision from(LegalDocumentRevision revision) {
            return new PublicRevision(revision.getId(), revision.getKind(), revision.getVersion(),
                    revision.getContentUz(), revision.getContentRu(), revision.getPublishedAt(), revision.isActive());
        }
    }

    public record Input(@NotBlank @Pattern(regexp = "[A-Za-z0-9._\\-]{1,40}") String version,
                        @NotBlank @Size(max = 30000) String contentUz,
                        @NotBlank @Size(max = 30000) String contentRu) {}
}
