package uz.osonmarket.domain.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legal_document_revisions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_kind", "version"}))
public class LegalDocumentRevision {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "document_kind", nullable = false, length = 16)
    private String kind;
    @Column(nullable = false, length = 40)
    private String version;
    @Column(nullable = false, columnDefinition = "text")
    private String contentUz;
    @Column(nullable = false, columnDefinition = "text")
    private String contentRu;
    @Column(nullable = false)
    private Instant publishedAt;
    private UUID publishedBy;
    private Boolean active;

    protected LegalDocumentRevision() {}

    public LegalDocumentRevision(String kind, String version, String contentUz, String contentRu, UUID publishedBy) {
        this.publishedBy = publishedBy;
        this.active = true;
        this.kind = kind;
        this.version = version;
        this.contentUz = contentUz;
        this.contentRu = contentRu;
        this.publishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPublishedBy() { return publishedBy; }
    public boolean isActive() { return Boolean.TRUE.equals(active); }

    public String getKind() { return kind; }
    public String getVersion() { return version; }
    public String getContentUz() { return contentUz; }
    public String getContentRu() { return contentRu; }
    public Instant getPublishedAt() { return publishedAt; }
}
