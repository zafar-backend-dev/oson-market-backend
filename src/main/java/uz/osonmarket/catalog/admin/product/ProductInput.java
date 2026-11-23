package uz.osonmarket.catalog.admin.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record ProductInput(@NotBlank @Size(max = 200) String slug, UUID brandId,
                           @NotEmpty Set<@NotNull UUID> categoryIds,
                           @NotBlank @Size(max = 32) String status,
                           @Size(max = 32) String productKind, @Min(0) Integer warrantyMonths,
                           @Valid Text uz, @Valid Text ru) {
    public record Text(@NotBlank @Size(max = 255) String title, String shortDescription,
                       String description, @Size(max = 255) String seoTitle, String seoDescription) {}
}
