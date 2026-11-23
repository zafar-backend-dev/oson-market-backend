package uz.osonmarket.catalog.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "category_image_contents")
class CategoryImageContent {
    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected CategoryImageContent() {}

    CategoryImageContent(UUID id, byte[] content) {
        this.id = id;
        this.content = content;
    }

    byte[] content() {
        return content;
    }
}
