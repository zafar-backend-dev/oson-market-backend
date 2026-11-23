package uz.osonmarket.catalog.admin.image;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ImageInput(@NotNull UUID productTypeId, @NotNull UUID mediaId,
                         @Min(0) Integer sortOrder, Boolean primaryImage) {}
