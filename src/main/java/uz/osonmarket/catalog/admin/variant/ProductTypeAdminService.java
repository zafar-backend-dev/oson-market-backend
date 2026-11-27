package uz.osonmarket.catalog.admin.variant;

import java.time.Instant;
import java.util.UUID;
import uz.osonmarket.catalog.price.CatalogPrices;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.catalog.admin.shared.CatalogPersistence;
import uz.osonmarket.catalog.admin.shared.Entry;
import uz.osonmarket.catalog.persistence.ProductTypeRepository;
import uz.osonmarket.domain.catalog.Product;
import uz.osonmarket.domain.catalog.ProductType;

@Service
public class ProductTypeAdminService {
    private final CatalogPersistence persistence;
    private final ProductTypeRepository repository;
    private final CatalogPrices prices;

    public ProductTypeAdminService(CatalogPersistence persistence, ProductTypeRepository repository, CatalogPrices prices) {
        this.persistence = persistence;
        this.repository = repository;
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public PagedModel<Entry<TypeInput>> listProductType(Pageable pageable) {
        return new PagedModel<>(repository.findAll(persistence.page(pageable)).map(this::productTypeView));
    }

    @Transactional(readOnly = true)
    public Entry<TypeInput> getProductType(UUID id) {
        return productTypeView(persistence.require(ProductType.class, id));
    }

    @Transactional
    public Entry<TypeInput> saveProductType(UUID id, TypeInput input) {
        ProductType entity = id == null ? new ProductType() : persistence.require(ProductType.class, id);
        entity.setProduct(persistence.require(Product.class, input.productId()));
        entity.setSku(input.sku().strip());
        if (input.titleUz() != null) entity.setTitleUz(input.titleUz());
        if (input.titleRu() != null) entity.setTitleRu(input.titleRu());
        entity.setBarcode(input.barcode() == null || input.barcode().isBlank() ? null : input.barcode().strip());
        entity.setStatus(input.status());
        entity.setWeightGrams(input.weightGrams());
        entity.setLengthMm(input.lengthMm());
        entity.setWidthMm(input.widthMm());
        entity.setHeightMm(input.heightMm());
        entity.setDefaultType(input.defaultType());
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
        return productTypeView(entity);
    }

    @Transactional
    public void deleteProductType(UUID id) {
        ProductType entity = persistence.require(ProductType.class, id);
        if (!entity.getImages().isEmpty()) {
            throw persistence.failedConflict();
        }
        prices.delete(id);
        repository.delete(entity);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    private Entry<TypeInput> productTypeView(ProductType entity) {
        return new Entry<>(entity.getId(), new TypeInput(persistence.id(entity.getProduct()), entity.getSku(), entity.getTitleUz(), entity.getTitleRu(), entity.getBarcode(), entity.getStatus(),
                entity.getWeightGrams(), entity.getLengthMm(), entity.getWidthMm(), entity.getHeightMm(), entity.getDefaultType()));
    }
}
