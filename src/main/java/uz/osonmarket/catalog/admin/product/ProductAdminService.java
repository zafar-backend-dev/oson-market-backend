package uz.osonmarket.catalog.admin.product;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.catalog.admin.shared.CatalogPersistence;
import uz.osonmarket.catalog.admin.shared.Entry;
import uz.osonmarket.catalog.persistence.ProductRepository;
import uz.osonmarket.domain.catalog.Brand;
import uz.osonmarket.domain.catalog.Category;
import uz.osonmarket.domain.catalog.Product;
import uz.osonmarket.domain.catalog.ProductTranslation;
import uz.osonmarket.domain.reference.Language;

@Service
public class ProductAdminService {
    private final CatalogPersistence persistence;
    private final ProductRepository repository;
    private final AuthLocks locks;
    private final EntityManager em;

    public ProductAdminService(CatalogPersistence persistence, ProductRepository repository, AuthLocks locks, EntityManager em) {
        this.persistence = persistence;
        this.repository = repository;
        this.locks = locks;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public PagedModel<Entry<ProductInput>> listProduct(Pageable pageable, UUID categoryId) {
        Pageable page = persistence.page(pageable);
        var result = categoryId == null ? repository.findAll(page) : repository.findByCategories_Id(categoryId, page);
        return new PagedModel<>(result.map(this::productView));
    }

    @Transactional(readOnly = true)
    public Entry<ProductInput> getProduct(UUID id) {
        return productView(persistence.require(Product.class, id));
    }

    @Transactional
    public Entry<ProductInput> saveProduct(UUID id, ProductInput input) {
        if (id != null) {
            locks.lock("catalog:product:" + id);
        }
        Product entity = id == null ? new Product() : persistence.require(Product.class, id);
        entity.setSlug(input.slug().strip());
        entity.setBrand(persistence.optional(Brand.class, input.brandId()));
        var categories = input.categoryIds().stream().map(categoryId -> persistence.require(Category.class, categoryId)).toList();
        entity.getCategories().retainAll(categories);
        entity.getCategories().addAll(categories);
        entity.setStatus(input.status().strip());
        entity.setProductKind(input.productKind());
        entity.setWarrantyMonths(input.warrantyMonths());
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        try {
            repository.saveAndFlush(entity);
            saveText(entity, "uz", input.uz());
            saveText(entity, "ru", input.ru());
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
        return productView(entity);
    }

    private Entry<ProductInput> productView(Product entity) {
        return new Entry<>(entity.getId(), new ProductInput(entity.getSlug(), persistence.id(entity.getBrand()),
                entity.getCategories().stream().map(Category::getId).collect(Collectors.toSet()),
                entity.getStatus(), entity.getProductKind(), entity.getWarrantyMonths(), text(entity, "uz"), text(entity, "ru")));
    }

    void deleteTranslations(UUID id) {
        em.createQuery("delete from ProductTranslation t where t.product.id = :id").setParameter("id", id).executeUpdate();
    }

    private ProductTranslation translation(Product product, String language) {
        return em.createQuery("select t from ProductTranslation t where t.product = :product and t.language.code = :language",
                        ProductTranslation.class)
                .setParameter("product", product).setParameter("language", language)
                .getResultList().stream().findFirst().orElse(null);
    }

    private ProductInput.Text text(Product product, String language) {
        var item = translation(product, language);
        return item == null ? null : new ProductInput.Text(item.getTitle(), item.getShortDescription(),
                item.getDescription(), item.getSeoTitle(), item.getSeoDescription());
    }

    private void saveText(Product product, String language, ProductInput.Text input) {
        if (input == null) {
            return;
        }
        ProductTranslation item = translation(product, language);
        boolean isNew = item == null;
        if (isNew) {
            item = new ProductTranslation(product, persistence.require(Language.class, language));
        }
        item.update(input.title().strip(), input.shortDescription(), input.description(), input.seoTitle(), input.seoDescription());
        if (isNew) {
            em.persist(item);
        }
    }
}
