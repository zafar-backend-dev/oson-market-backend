package uz.osonmarket.catalog.admin.brand;

import java.util.UUID;

public record BrandResponse(UUID id, BrandInput data, String logoUrl) {}
