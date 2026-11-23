package uz.osonmarket.catalog.admin.product;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uz.osonmarket.catalog.price.CatalogPrices.Price;

public record ProductDetail(UUID id, ProductInput data, List<Variant> variants,
                            Instant createdAt, Instant updatedAt, Instant publishedAt) {
    public record Variant(UUID id, String sku, String barcode, String status, Integer weightGrams,
                          Integer lengthMm, Integer widthMm, Integer heightMm, Boolean defaultType,
                          List<Picture> images, Price price) {}

    public record Picture(UUID id, String imageUrl, Integer sortOrder, Boolean primaryImage) {}
}
