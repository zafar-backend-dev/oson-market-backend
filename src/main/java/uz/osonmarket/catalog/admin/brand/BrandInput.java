package uz.osonmarket.catalog.admin.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BrandInput(@NotBlank @Size(max = 120) String slug, @NotBlank @Size(max = 180) String name,
                         UUID logoMediaId, @NotNull Boolean active) {
}
