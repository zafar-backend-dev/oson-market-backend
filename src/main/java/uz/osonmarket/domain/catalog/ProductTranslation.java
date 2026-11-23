package uz.osonmarket.domain.catalog;

import jakarta.persistence.*;
import java.util.UUID;
import uz.osonmarket.domain.reference.Language;

@Entity
@Table(name = "product_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "language_code"}))
public class ProductTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "language_code")
    private Language language;

    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "text")
    private String shortDescription;
    @Column(columnDefinition = "text")
    private String description;
    @Column(length = 255)
    private String seoTitle;
    @Column(columnDefinition = "text")
    private String seoDescription;

    protected ProductTranslation() {}

    public ProductTranslation(Product product, Language language) {
        this.product = product;
        this.language = language;
    }

    public void update(String title, String shortDescription, String description, String seoTitle, String seoDescription) {
        this.title = title;
        this.shortDescription = shortDescription;
        this.description = description;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
    }

    public String getTitle() { return title; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public String getSeoTitle() { return seoTitle; }
    public String getSeoDescription() { return seoDescription; }
}
