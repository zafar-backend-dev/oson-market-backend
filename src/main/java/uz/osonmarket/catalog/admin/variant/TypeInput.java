package uz.osonmarket.catalog.admin.variant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TypeInput(@NotNull UUID productId, @NotBlank @Size(max = 100) String sku,
                          @Size(max = 150) String titleUz, @Size(max = 150) String titleRu,
                        @Size(max = 100) String barcode, @Size(max = 32) String status,
                        @Min(0) Integer weightGrams, @Min(0) Integer lengthMm,
                        @Min(0) Integer widthMm, @Min(0) Integer heightMm, Boolean defaultType) {
}
