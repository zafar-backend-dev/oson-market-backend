package uz.osonmarket.catalog.admin.category;

import java.util.UUID;

public record CategoryResponse(UUID id, CategoryInput data, String imageUrl) {}
