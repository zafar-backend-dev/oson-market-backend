package uz.osonmarket.catalog.customer.tree;

import java.util.List;
import java.util.UUID;
import uz.osonmarket.catalog.price.CatalogPrices.Price;

public record CatalogTree(List<Node> categories, List<ProductItem> uncategorizedProducts) {
    public record Node(UUID id, UUID parentId, String slug, String name, String description,
                       String seoTitle, String seoDescription, String language, String imageUrl,
                       Integer sortOrder, List<Node> children, List<ProductItem> products) {}

    public record ProductItem(UUID id, String slug, String title, String shortDescription, String description,
                              String seoTitle, String seoDescription, String language, BrandItem brand,
                              String productKind, Integer warrantyMonths, List<Variant> variants) {}

    public record BrandItem(UUID id, String name, String slug, String logoUrl) {}

    public record Variant(UUID id, String sku, String title, Integer weightGrams, Integer lengthMm, Integer widthMm,
                          Integer heightMm, boolean defaultType, List<Picture> images, Price price) {}

    public record Picture(UUID id, String imageUrl, Integer sortOrder, boolean primaryImage) {}
}
