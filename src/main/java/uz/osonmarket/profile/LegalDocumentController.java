package uz.osonmarket.profile;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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

    public LegalDocumentController(EntityManager em) { this.em = em; }

    @GetMapping("/api/site/legal/{kind}")
    public ResponseEntity<LegalDocumentRevision> latest(@PathVariable String kind) {
        var result = revisions(kind, 1);
        return result.isEmpty() ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.get(0));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/admin/legal/{kind}")
    public List<LegalDocumentRevision> history(@PathVariable String kind) {
        return revisions(kind, 50);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/admin/legal/{kind}")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public LegalDocumentRevision publish(@PathVariable String kind, @Valid @RequestBody Input input) {
        requireKind(kind);
        var revision = new LegalDocumentRevision(kind, input.version(), input.contentUz().strip(), input.contentRu().strip());
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

    public record Input(@NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,40}") String version,
                        @NotBlank @Size(max = 30000) String contentUz,
                        @NotBlank @Size(max = 30000) String contentRu) {}
}
