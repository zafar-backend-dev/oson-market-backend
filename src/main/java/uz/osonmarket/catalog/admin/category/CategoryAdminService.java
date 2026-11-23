package uz.osonmarket.catalog.admin.category;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.catalog.admin.shared.CatalogPersistence;
import uz.osonmarket.catalog.media.CatalogImages;
import uz.osonmarket.profile.AvatarImages.Image;
import uz.osonmarket.catalog.persistence.CategoryRepository;
import uz.osonmarket.domain.catalog.Category;
import uz.osonmarket.domain.catalog.CategoryTranslation;
import uz.osonmarket.domain.media.MediaFile;
import uz.osonmarket.domain.reference.Language;

@Service
public class CategoryAdminService {
    private final CatalogPersistence persistence;
    private final CategoryRepository repository;
    private final EntityManager em;
    private final AuthLocks locks;
    private final CatalogImages images;

    public CategoryAdminService(CatalogPersistence persistence, CategoryRepository repository, EntityManager em, AuthLocks locks, CatalogImages images) {
        this.persistence = persistence;
        this.repository = repository;
        this.em = em;
        this.locks = locks;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public PagedModel<CategoryResponse> listCategory(Pageable pageable) {
        return new PagedModel<>(repository.findAll(persistence.page(pageable)).map(this::categoryView));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(UUID id) {
        return categoryView(persistence.require(Category.class, id));
    }

    @Transactional
    public CategoryResponse saveCategory(UUID id, CategoryInput input) {
        return saveCategory(id, input, null, false, false);
    }

    @Transactional(readOnly = true)
    public byte[] image(UUID id) {
        return images.read(persistence.require(Category.class, id));
    }

    @Transactional
    public CategoryResponse saveCategory(UUID id, CategoryInput input, Image file,
                                         boolean removeImage, boolean keepImageIfAbsent) {
        if (file != null && removeImage) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "request.invalid");
        }
        locks.lock("catalog:categories");
        Category entity = id == null ? new Category() : persistence.require(Category.class, id);
        Category parent = persistence.optional(Category.class, input.parentId());
        Set<UUID> visited = new HashSet<>();
        for (Category cursor = parent; cursor != null; cursor = cursor.getParent()) {
            if (Objects.equals(cursor.getId(), entity.getId()) || !visited.add(cursor.getId())) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "catalog.category.cycle");
            }
        }
        entity.setParent(parent);
        entity.setSlug(input.slug().strip());
        if (file != null) {
            entity.setImageMedia(images.store(file));
        } else if (removeImage) {
            entity.setImageMedia(null);
        } else if (!keepImageIfAbsent || input.imageMediaId() != null) {
            entity.setImageMedia(persistence.optional(MediaFile.class, input.imageMediaId()));
        }
        entity.setSortOrder(input.sortOrder());
        entity.setActive(input.active());
        try {
            repository.saveAndFlush(entity);
            saveTranslation(entity, "uz", input.uz());
            saveTranslation(entity, "ru", input.ru());
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
        return categoryView(entity);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        locks.lock("catalog:categories");
        Category entity = persistence.require(Category.class, id);
        if (!entity.getChildren().isEmpty() || !entity.getProducts().isEmpty()) {
            throw persistence.failedConflict();
        }
        em.createQuery("delete from CategoryTranslation t where t.category.id = :id")
                .setParameter("id", id).executeUpdate();
        repository.delete(entity);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    private CategoryResponse categoryView(Category entity) {
        return new CategoryResponse(entity.getId(), new CategoryInput(entity.getSlug(), persistence.id(entity.getParent()), persistence.id(entity.getImageMedia()),
                entity.getSortOrder(), entity.getActive(), translation(entity, "uz"), translation(entity, "ru")), images.url(entity, true));
    }

    private CategoryTranslation findTranslation(Category category, String code) {
        return em.createQuery("select t from CategoryTranslation t where t.category = :category and t.language.code = :code",
                        CategoryTranslation.class)
                .setParameter("category", category).setParameter("code", code).getResultList().stream().findFirst().orElse(null);
    }

    private Translation translation(Category category, String code) {
        CategoryTranslation item = findTranslation(category, code);
        return item == null ? null : new Translation(item.getName(), item.getDescription(),
                item.getSeoTitle(), item.getSeoDescription());
    }

    private void saveTranslation(Category category, String code, Translation input) {
        CategoryTranslation translation = findTranslation(category, code);
        boolean isNew = translation == null;
        if (isNew) {
            translation = new CategoryTranslation(category, persistence.require(Language.class, code));
        }
        translation.update(input.name().strip(), input.description(), input.seoTitle(), input.seoDescription());
        if (isNew) {
            em.persist(translation);
        }
    }
}
