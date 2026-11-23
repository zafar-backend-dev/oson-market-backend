package uz.osonmarket.catalog.customer.category;

import java.util.UUID;

public record CategoryView(UUID id, UUID parentId, String slug, String name, String description,
                           String seoTitle, String seoDescription, String language,
                           UUID imageMediaId, String imageUrl, Integer sortOrder, boolean hasChildren) {
}
