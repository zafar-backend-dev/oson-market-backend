package uz.osonmarket.domain.catalog;

import jakarta.persistence.*;
import java.util.UUID;
import uz.osonmarket.domain.reference.Language;

@Entity
@Table(name = "category_translations", uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "language_code"}))
public class CategoryTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "language_code", nullable = false)
    private Language language;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String seoTitle;

    @Column(columnDefinition = "text")
    private String seoDescription;

    protected CategoryTranslation() {}

    public CategoryTranslation(Category category, Language language) {
        this.category = category;
        this.language = language;
    }

    public void update(String name, String description, String seoTitle, String seoDescription) {
        this.name = name;
        this.description = description;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
    }

    public String getLanguageCode() { return language.getCode(); }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSeoTitle() { return seoTitle; }
    public String getSeoDescription() { return seoDescription; }
}
