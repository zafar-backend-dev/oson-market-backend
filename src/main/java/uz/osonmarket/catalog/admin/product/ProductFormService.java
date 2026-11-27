package uz.osonmarket.catalog.admin.product;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.catalog.admin.shared.CatalogPersistence;
import uz.osonmarket.catalog.media.CatalogImages;
import uz.osonmarket.catalog.price.CatalogPrices;
import uz.osonmarket.catalog.persistence.ProductRepository;
import uz.osonmarket.catalog.persistence.ProductTypeImageRepository;
import uz.osonmarket.catalog.persistence.ProductTypeRepository;
import uz.osonmarket.domain.catalog.Product;
import uz.osonmarket.domain.catalog.ProductType;
import uz.osonmarket.domain.catalog.ProductTypeImage;
import uz.osonmarket.profile.AvatarImages;

@Service
public class ProductFormService {
    private final ProductAdminService products;
    private final ProductRepository productRepository;
    private final ProductTypeRepository variants;
    private final ProductTypeImageRepository pictures;
    private final CatalogPersistence persistence;
    private final AuthLocks locks;
    private final CatalogImages media;
    private final AvatarImages decoder;
    private final CatalogPrices prices;

    public ProductFormService(ProductAdminService products, ProductRepository productRepository,
                              ProductTypeRepository variants, ProductTypeImageRepository pictures,
                              CatalogPersistence persistence, AuthLocks locks, CatalogImages media,
                              AvatarImages decoder, CatalogPrices prices) {
        this.products = products;
        this.productRepository = productRepository;
        this.variants = variants;
        this.pictures = pictures;
        this.persistence = persistence;
        this.locks = locks;
        this.media = media;
        this.decoder = decoder;
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public ProductDetail get(UUID productId) {
        var product = products.getProduct(productId);
        Product entity = persistence.require(Product.class, productId);
        return new ProductDetail(product.id(), product.data(), variants.findByProductIdOrderByIdAsc(productId)
                .stream().map(this::variantView).toList(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getPublishedAt());
    }

    @Transactional
    public ProductDetail save(UUID id, ProductForm form, MultiValueMap<String, MultipartFile> files) {
        checkFiles(form, files);
        try {
            UUID productId = products.saveProduct(id, form.product()).id();
            Product product = persistence.require(Product.class, productId);
            var existing = byId(variants.findByProductIdOrderByIdAsc(productId), ProductType::getId);
            Set<UUID> retained = new HashSet<>();
            for (var input : form.variants()) {
                if (input.id() != null && (!existing.containsKey(input.id()) || !retained.add(input.id()))) {
                    throw invalid();
                }
            }
            existing.values().stream().filter(variant -> !retained.contains(variant.getId()))
                    .forEach(this::deleteVariant);

            for (var input : form.variants()) {
                ProductType variant = input.id() == null ? new ProductType() : existing.get(input.id());
                applyVariant(product, variant, input);
                variants.saveAndFlush(variant);
                prices.save(variant, input.regularPrice(), input.salePrice());
                savePictures(variant, input.images(), files);
            }
            pictures.flush();
            return get(productId);
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    @Transactional
    public void delete(UUID productId) {
        locks.lock("catalog:product:" + productId);
        Product product = persistence.require(Product.class, productId);
        try {
            variants.findByProductIdOrderByIdAsc(productId).forEach(this::deleteVariant);
            products.deleteTranslations(productId);
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    @Transactional(readOnly = true)
    public byte[] image(UUID productId, UUID imageId) {
        return media.readMedia(requireImage(productId, imageId).getMedia());
    }

    private ProductTypeImage requireImage(UUID productId, UUID imageId) {
        ProductTypeImage image = persistence.require(ProductTypeImage.class, imageId);
        if (image.getProductType() == null || image.getProductType().getProduct() == null
                || !productId.equals(image.getProductType().getProduct().getId())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
        }
        return image;
    }

    @Transactional
    public void removeVariant(UUID productId, UUID variantId) {
        locks.lock("catalog:product:" + productId);
        ProductType variant = requireVariant(productId, variantId);
        try {
            deleteVariant(variant);
            variant.getProduct().setUpdatedAt(Instant.now());
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    @Transactional
    public void removeImage(UUID productId, UUID imageId) {
        locks.lock("catalog:product:" + productId);
        ProductTypeImage image = requireImage(productId, imageId);
        try {
            image.getProductType().getProduct().setUpdatedAt(Instant.now());
            pictures.delete(image);
            pictures.flush();
        } catch (DataIntegrityViolationException ex) {
            throw persistence.failedConflict();
        }
    }

    private ProductType requireVariant(UUID productId, UUID variantId) {
        ProductType variant = persistence.require(ProductType.class, variantId);
        if (variant.getProduct() == null || !productId.equals(variant.getProduct().getId())) {
            throw new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
        }
        return variant;
    }

    private void savePictures(ProductType variant, List<ProductForm.Picture> input,
                              MultiValueMap<String, MultipartFile> files) {
        var existing = byId(pictures.findByProductTypeIdOrderBySortOrderAscIdAsc(variant.getId()), ProductTypeImage::getId);
        for (var item : input) {
            ProductTypeImage image;
            if (item.id() == null) {
                image = new ProductTypeImage();
                image.setProductType(variant);
            } else {
                image = existing.remove(item.id());
                if (image == null) {
                    throw invalid();
                }
            }
            if (item.filePart() != null) {
                image.setMedia(media.store(decoder.read(files.getFirst(item.filePart())), "product"));
            }
            image.setSortOrder(item.sortOrder());
            image.setPrimaryImage(item.primaryImage());
            pictures.save(image);
        }
        pictures.deleteAll(existing.values());
    }

    private void deleteVariant(ProductType variant) {
        pictures.deleteAll(pictures.findByProductTypeIdOrderBySortOrderAscIdAsc(variant.getId()));
        pictures.flush();
        prices.delete(variant.getId());
        variants.delete(variant);
        variants.flush();
    }

    private void checkFiles(ProductForm form, MultiValueMap<String, MultipartFile> files) {
        Set<String> expected = new HashSet<>();
        for (var variant : form.variants()) {
            for (var image : variant.images()) {
                String part = image.filePart();
                if ((image.id() == null && part == null) || (part != null && !expected.add(part))) {
                    throw invalid();
                }
            }
        }
        Set<String> provided = new HashSet<>(files.keySet());
        provided.remove("product"); // Some clients give the JSON part a filename.
        if (expected.size() > 12 || !expected.equals(provided)
                || expected.stream().anyMatch(name -> files.get(name).size() != 1)) {
            throw invalid();
        }
    }

    private void applyVariant(Product product, ProductType variant, ProductForm.Variant input) {
        variant.setProduct(product);
        variant.setSku(input.sku().strip());
        if (input.titleUz() != null) variant.setTitleUz(input.titleUz());
        if (input.titleRu() != null) variant.setTitleRu(input.titleRu());
        variant.setBarcode(input.barcode() == null || input.barcode().isBlank() ? null : input.barcode().strip());
        variant.setStatus(input.status());
        variant.setWeightGrams(input.weightGrams());
        variant.setLengthMm(input.lengthMm());
        variant.setWidthMm(input.widthMm());
        variant.setHeightMm(input.heightMm());
        variant.setDefaultType(input.defaultType());
        Instant now = Instant.now();
        if (variant.getCreatedAt() == null) {
            variant.setCreatedAt(now);
        }
        variant.setUpdatedAt(now);
    }

    private ProductDetail.Variant variantView(ProductType variant) {
        String base = "/api/admin/products/" + variant.getProduct().getId() + "/images/";
        var images = pictures.findByProductTypeIdOrderBySortOrderAscIdAsc(variant.getId()).stream()
                .map(image -> new ProductDetail.Picture(image.getId(), media.isStored(image.getMedia())
                        ? base + image.getId() : null, image.getSortOrder(), image.getPrimaryImage())).toList();
        return new ProductDetail.Variant(variant.getId(), variant.getSku(), variant.getTitleUz(), variant.getTitleRu(), variant.getBarcode(), variant.getStatus(),
                variant.getWeightGrams(), variant.getLengthMm(), variant.getWidthMm(), variant.getHeightMm(),
                variant.getDefaultType(), images, prices.current(variant.getId()));
    }

    private <T> Map<UUID, T> byId(List<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "request.invalid");
    }
}
