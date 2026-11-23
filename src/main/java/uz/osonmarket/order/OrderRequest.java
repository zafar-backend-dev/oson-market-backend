package uz.osonmarket.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import uz.osonmarket.branch.BranchAdminController.Location;

public record OrderRequest(@NotNull UUID requestId,
                           @NotBlank @Size(max = 32) String phone,
                           @NotBlank @Size(max = 64) String telegramAddress,
                           @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> items,
                           @Valid Fulfillment fulfillment) {
    public record Fulfillment(@NotNull FulfillmentMethod method, @NotNull UUID branchId,
                              @Size(max = 255) String deliveryAddress, @Valid Location location) {}
    public record Item(@NotNull UUID variantId, @Min(1) @Max(99) int quantity) {

    }
}
