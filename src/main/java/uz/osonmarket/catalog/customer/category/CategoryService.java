package uz.osonmarket.catalog.customer.category;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.catalog.persistence.CategoryRepository;
import uz.osonmarket.catalog.media.CatalogImages;
import uz.osonmarket.domain.catalog.Category;
import uz.osonmarket.domain.catalog.CategoryTranslation;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final EntityManager em;
    private final CatalogImages images;

    public CategoryService(CategoryRepository categories, EntityManager em, CatalogImages images) {
        this.categories = categories;
        this.em = em;
        this.images = images;
    }

    public List<CategoryView> list(UUID parentId, String language) {
        if (parentId != null) {
            visibleCategory(parentId);
        }
        var children = parentId == null
                ? categories.findByParentIsNullAndActiveTrueOrderBySortOrderAscIdAsc()
                : categories.findByParent_IdAndActiveTrueOrderBySortOrderAscIdAsc(parentId);
        return children.stream().map(category -> view(category, language)).toList();
    }

    public CategoryView get(UUID id, String language) {
        return view(visibleCategory(id), language);
    }

    public byte[] image(UUID id) {
        return images.read(visibleCategory(id));
    }

    private Category visibleCategory(UUID id) {
        Category category = categories.findById(id).orElseThrow(this::notFound);
        Set<UUID> visited = new HashSet<>();
        for (Category current = category; current != null; current = current.getParent()) {
            if (!Boolean.TRUE.equals(current.getActive()) || !visited.add(current.getId())) {
                throw notFound();
            }
        }
        return category;
    }

    private CategoryView view(Category category, String language) {
        var translation = em.createQuery("""
                select t from CategoryTranslation t
                where t.category.id = :id and t.language.code in (:requested, 'uz', 'ru')
                order by case when t.language.code = :requested then 0
                              when t.language.code = 'uz' then 1 else 2 end
                """, CategoryTranslation.class)
                .setParameter("id", category.getId())
                .setParameter("requested", language)
                .setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        return new CategoryView(
                category.getId(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getSlug(),
                translation == null ? category.getSlug() : translation.getName(),
                translation == null ? null : translation.getDescription(),
                translation == null ? null : translation.getSeoTitle(),
                translation == null ? null : translation.getSeoDescription(),
                translation == null ? null : translation.getLanguageCode(),
                category.getImageMedia() == null ? null : category.getImageMedia().getId(),
                images.url(category, false),
                category.getSortOrder(),
                categories.existsByParent_IdAndActiveTrue(category.getId()));
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
    }
}
