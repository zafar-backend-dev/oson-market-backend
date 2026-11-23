package uz.osonmarket.catalog.admin.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Translation(@NotBlank @Size(max = 200) String name, String description,
                          @Size(max = 255) String seoTitle, String seoDescription) {
}
