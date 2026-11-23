package uz.osonmarket.catalog.admin.brand;

import java.util.Locale;
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
import uz.osonmarket.catalog.persistence.BrandRepository;
import uz.osonmarket.domain.catalog.Brand;
import uz.osonmarket.domain.media.MediaFile;
import uz.osonmarket.profile.AvatarImages.Image;

@Service
public class BrandAdminService {
    private final CatalogPersistence persistence;
    private final BrandRepository repository;
    private final CatalogImages images;
    private final AuthLocks locks;

    public BrandAdminService(CatalogPersistence persistence, BrandRepository repository,
                             CatalogImages images, AuthLocks locks) {
        this.persistence = persistence;
        this.repository = repository;
        this.images = images;
        this.locks = locks;
    }

    @Transactional(readOnly = true)
    public PagedModel<BrandResponse> listBrand(Pageable pageable, String query, Boolean active) {
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return new PagedModel<>(repository.search(term, active, persistence.page(pageable)).map(this::brandView));
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrand(UUID id) {
        return brandView(persistence.require(Brand.class, id));
    }

    @Transactional(readOnly = true)
    public byte[] logo(UUID id) {
        return images.readMedia(persistence.require(Brand.class, id).getLogoMedia());
    }

    @Transactional
    public BrandResponse saveBrand(UUID id, BrandInput input) {
        return save(id, input, null, false, false);
    }

    @Transactional
    public BrandResponse saveBrandWithLogo(UUID id, BrandInput input, Image logo, boolean removeLogo) {
        if (removeLogo && logo != null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "request.invalid");
        }
        return save(id, input, logo, removeLogo, true);
    }

    private BrandResponse save(UUID id, BrandInput input, Image logo, boolean removeLogo, boolean retainLogo) {
        if (id != null) {
            locks.lock("catalog:brand:" + id);
        }
        Brand entity = id == null ? new Brand() : persistence.require(Brand.class, id);
        entity.setSlug(input.slug().strip());
        entity.setName(input.name().strip());
        entity.setActive(input.active());
        if (logo != null) {
            entity.setLogoMedia(images.store(logo, "brand"));
        } else if (removeLogo) {
            entity.setLogoMedia(null);
        } else if (!retainLogo || input.logoMediaId() != null) {
            entity.setLogoMedia(persistence.optional(MediaFile.class, input.logoMediaId()));
        }
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
        return brandView(entity);
    }

    @Transactional
    public void deleteBrand(UUID id) {
        locks.lock("catalog:brand:" + id);
        Brand entity = persistence.require(Brand.class, id);
        if (!entity.getProducts().isEmpty()) {
            throw persistence.failedConflict();
        }
        repository.delete(entity);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    private BrandResponse brandView(Brand entity) {
        MediaFile logo = entity.getLogoMedia();
        return new BrandResponse(entity.getId(), new BrandInput(entity.getSlug(), entity.getName(),
                persistence.id(logo), entity.getActive()),
                images.isStored(logo) ? "/api/admin/brands/" + entity.getId() + "/logo" : null);
    }
}
