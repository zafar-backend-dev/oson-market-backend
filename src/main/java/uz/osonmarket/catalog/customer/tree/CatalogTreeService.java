package uz.osonmarket.catalog.customer.tree;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.catalog.media.CatalogImages;
import uz.osonmarket.catalog.price.CatalogPrices;
import uz.osonmarket.domain.catalog.*;

@Service
@Transactional(readOnly = true)
public class CatalogTreeService {
    private final EntityManager em;
    private final CatalogImages media;
    private final CatalogPrices prices;

    public CatalogTreeService(EntityManager em, CatalogImages media, CatalogPrices prices) {
        this.em = em;
        this.media = media;
        this.prices = prices;
    }

    public CatalogTree tree(String language) {
        var categories = em.createQuery("""
                select c from Category c left join fetch c.imageMedia
                where c.active = true order by c.sortOrder nulls last, c.id
                """, Category.class).getResultList();
        var children = new HashMap<UUID, List<Category>>();
        for (var category : categories) {
            children.computeIfAbsent(parentId(category), ignored -> new ArrayList<>()).add(category);
        }

        Map<UUID, List<CatalogTree.ProductItem>> byCategory = new HashMap<>();
        List<CatalogTree.ProductItem> uncategorized = new ArrayList<>();
        var products = em.createQuery("""
                select distinct p from Product p left join fetch p.categories
                    left join fetch p.brand b left join fetch b.logoMedia
                where p.status in ('ACTIVE', 'PUBLISHED') order by p.slug, p.id
                """, Product.class).getResultList();
        var texts = translations("ProductTranslation", "product", ProductTranslation.class, language);
        var typeViews = variants();
        for (var product : products) {
            UUID id = product.getId();
            var view = productView(product, texts.get(id), typeViews.getOrDefault(id, List.of()));
            if (product.getCategories().isEmpty()) {
                uncategorized.add(view);
            }
            for (var category : product.getCategories()) {
                byCategory.computeIfAbsent(category.getId(), ignored -> new ArrayList<>()).add(view);
            }
        }

        var categoryTexts = translations("CategoryTranslation", "category", CategoryTranslation.class, language);
        var roots = children.getOrDefault(null, List.of()).stream()
                .map(root -> node(root, children, byCategory, categoryTexts))
                .toList();
        return new CatalogTree(roots, uncategorized);
    }

    public byte[] productImage(UUID productId, UUID imageId) {
        var image = em.find(ProductTypeImage.class, imageId);
        var variant = image == null ? null : image.getProductType();
        var product = variant == null ? null : variant.getProduct();
        if (product == null || !productId.equals(product.getId()) || !visible(product) || !visible(variant)) {
            throw notFound();
        }
        return media.readMedia(image.getMedia());
    }

    public byte[] brandLogo(UUID id) {
        Brand brand = em.find(Brand.class, id);
        if (brand == null || !Boolean.TRUE.equals(brand.getActive())) {
            throw notFound();
        }
        return media.readMedia(brand.getLogoMedia());
    }

    private CatalogTree.Node node(Category category, Map<UUID, List<Category>> children,
                                  Map<UUID, List<CatalogTree.ProductItem>> products,
                                  Map<UUID, Localized<CategoryTranslation>> translations) {
        UUID id = category.getId();
        var localized = translations.get(id);
        var text = localized == null ? null : localized.text();
        String imageUrl = media.isStored(category.getImageMedia()) ? media.url(category, false) : null;
        return new CatalogTree.Node(id, parentId(category), category.getSlug(),
                text == null ? category.getSlug() : text.getName(),
                text == null ? null : text.getDescription(), text == null ? null : text.getSeoTitle(),
                text == null ? null : text.getSeoDescription(), localized == null ? null : localized.language(),
                imageUrl, category.getSortOrder(),
                children.getOrDefault(id, List.of()).stream().map(child -> node(child, children, products, translations)).toList(),
                products.getOrDefault(id, List.of()));
    }

    private CatalogTree.ProductItem productView(Product product, Localized<ProductTranslation> localized,
                                                List<CatalogTree.Variant> variants) {
        var t = localized == null ? null : localized.text();
        Brand brand = product.getBrand();
        CatalogTree.BrandItem brandView = null;
        if (brand != null && Boolean.TRUE.equals(brand.getActive())) {
            brandView = new CatalogTree.BrandItem(brand.getId(), brand.getName(), brand.getSlug(),
                    media.isStored(brand.getLogoMedia()) ? "/api/catalog/brands/" + brand.getId() + "/logo" : null);
        }
        return new CatalogTree.ProductItem(product.getId(), product.getSlug(), t == null ? product.getSlug() : t.getTitle(),
                t == null ? null : t.getShortDescription(), t == null ? null : t.getDescription(),
                t == null ? null : t.getSeoTitle(), t == null ? null : t.getSeoDescription(),
                localized == null ? null : localized.language(), brandView,
                product.getProductKind(), product.getWarrantyMonths(), variants);
    }

    private Map<UUID, List<CatalogTree.Variant>> variants() {
        var currentPrices = prices.publicPrices();
        var rows = em.createQuery("""
                select distinct v from ProductType v join fetch v.product p
                    left join fetch v.images i left join fetch i.media
                where p.status in ('ACTIVE', 'PUBLISHED')
                  and (v.status is null or v.status in ('ACTIVE', 'PUBLISHED'))
                order by v.id
                """, ProductType.class).getResultList();
        Map<UUID, List<CatalogTree.Variant>> result = new HashMap<>();
        for (var variant : rows) {
            UUID productId = variant.getProduct().getId();
            String imageBase = "/api/catalog/products/" + productId + "/images/";
            var pictures = variant.getImages().stream().filter(image -> media.isStored(image.getMedia()))
                    .map(image -> new CatalogTree.Picture(image.getId(), imageBase + image.getId(),
                            image.getSortOrder(), Boolean.TRUE.equals(image.getPrimaryImage()))).toList();
            result.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(new CatalogTree.Variant(
                    variant.getId(), variant.getSku(), variant.getWeightGrams(), variant.getLengthMm(),
                    variant.getWidthMm(), variant.getHeightMm(), Boolean.TRUE.equals(variant.getDefaultType()), pictures,
                    currentPrices.get(variant.getId())));
        }
        return result;
    }

    private <T> Map<UUID, Localized<T>> translations(String entity, String relation, Class<T> type, String language) {
        String query = "select t." + relation + ".id, t.language.code, t from " + entity
                + " t where t.language.code in ('uz', 'ru')"
                + " order by case when t.language.code = :requested then 0 when t.language.code = 'uz' then 1 else 2 end";
        Map<UUID, Localized<T>> result = new HashMap<>();
        for (Object[] row : em.createQuery(query, Object[].class).setParameter("requested", language).getResultList()) {
            result.putIfAbsent((UUID) row[0], new Localized<>((String) row[1], type.cast(row[2])));
        }
        return result;
    }

    public boolean isOrderable(ProductType variant) {
        return variant.getProduct() != null && visible(variant.getProduct()) && visible(variant);
    }

    public boolean visible(Product product) {
        return published(product.getStatus()) && (product.getCategories().isEmpty()
                || product.getCategories().stream().anyMatch(this::visibleBranch));
    }

    private boolean visible(ProductType variant) {
        return variant.getStatus() == null || published(variant.getStatus());
    }

    private boolean visibleBranch(Category category) {
        Set<UUID> visited = new HashSet<>();
        for (Category cursor = category; cursor != null; cursor = cursor.getParent()) {
            if (!Boolean.TRUE.equals(cursor.getActive()) || !visited.add(cursor.getId())) {
                return false;
            }
        }
        return true;
    }

    private boolean published(String status) {
        return "ACTIVE".equals(status) || "PUBLISHED".equals(status);
    }

    private UUID parentId(Category category) {
        return category.getParent() == null ? null : category.getParent().getId();
    }

    private AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
    }

    private record Localized<T>(String language, T text) {}
}
