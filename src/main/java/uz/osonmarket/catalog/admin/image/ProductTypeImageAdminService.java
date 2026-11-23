package uz.osonmarket.catalog.admin.image;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.catalog.admin.shared.CatalogPersistence;
import uz.osonmarket.catalog.admin.shared.Entry;
import uz.osonmarket.catalog.persistence.ProductTypeImageRepository;
import uz.osonmarket.domain.catalog.ProductType;
import uz.osonmarket.domain.catalog.ProductTypeImage;
import uz.osonmarket.domain.media.MediaFile;

@Service
public class ProductTypeImageAdminService {
    private final CatalogPersistence persistence;
    private final ProductTypeImageRepository repository;

    public ProductTypeImageAdminService(CatalogPersistence persistence, ProductTypeImageRepository repository) {
        this.persistence = persistence;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PagedModel<Entry<ImageInput>> listProductTypeImage(Pageable pageable) {
        return new PagedModel<>(repository.findAll(persistence.page(pageable)).map(this::productTypeImageView));
    }

    @Transactional(readOnly = true)
    public Entry<ImageInput> getProductTypeImage(UUID id) {
        return productTypeImageView(persistence.require(ProductTypeImage.class, id));
    }

    @Transactional
    public Entry<ImageInput> saveProductTypeImage(UUID id, ImageInput input) {
        ProductTypeImage entity = id == null ? new ProductTypeImage() : persistence.require(ProductTypeImage.class, id);
        entity.setProductType(persistence.require(ProductType.class, input.productTypeId()));
        entity.setMedia(persistence.require(MediaFile.class, input.mediaId()));
        entity.setSortOrder(input.sortOrder());
        entity.setPrimaryImage(input.primaryImage());
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
        return productTypeImageView(entity);
    }

    @Transactional
    public void deleteProductTypeImage(UUID id) {
        ProductTypeImage entity = persistence.require(ProductTypeImage.class, id);
        repository.delete(entity);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    private Entry<ImageInput> productTypeImageView(ProductTypeImage entity) {
        return new Entry<>(entity.getId(), new ImageInput(persistence.id(entity.getProductType()), persistence.id(entity.getMedia()), entity.getSortOrder(), entity.getPrimaryImage()));
    }
}
