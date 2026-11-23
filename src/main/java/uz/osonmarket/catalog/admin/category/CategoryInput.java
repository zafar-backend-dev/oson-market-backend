package uz.osonmarket.catalog.admin.category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryInput(@NotBlank @Size(max = 160) String slug, UUID parentId, UUID imageMediaId,
                            @Min(0) Integer sortOrder, @NotNull Boolean active,
                            @NotNull @Valid Translation uz, @NotNull @Valid Translation ru) {
}
