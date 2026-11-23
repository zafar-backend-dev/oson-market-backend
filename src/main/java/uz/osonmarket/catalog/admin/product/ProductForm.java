package uz.osonmarket.catalog.admin.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductForm(@NotNull @Valid ProductInput product,
                          @NotEmpty @Size(max = 100) List<@NotNull @Valid Variant> variants) {
    public record Variant(UUID id, @NotBlank @Size(max = 100) String sku,
                          @Size(max = 100) String barcode, @Size(max = 32) String status,
                          @Min(0) Integer weightGrams, @Min(0) Integer lengthMm,
                          @Min(0) Integer widthMm, @Min(0) Integer heightMm,
                          Boolean defaultType,
                          @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal regularPrice,
                          @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal salePrice,
                          @NotNull @Size(max = 20) List<@NotNull @Valid Picture> images) {}

    public record Picture(UUID id, @Pattern(regexp = "file_[A-Za-z0-9_-]{1,60}") String filePart,
                          @Min(0) Integer sortOrder, Boolean primaryImage) {}
}
