package uz.osonmarket.auth.admin;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public PagedModel<AdminUserView> list(@RequestParam(defaultValue = "") @Size(max = 255) String q,
                                         @RequestParam(required = false) Role role,
                                         @RequestParam(required = false) Status status,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(q, role, status, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserView details(@PathVariable UUID id) {
        return users.get(id);
    }

    @PatchMapping("/{id}/status")
    public AdminUserView updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusInput input) {
        return users.updateStatus(id, input.status());
    }

    public enum Role { USER, ADMIN }
    public enum Status { ACTIVE, BLOCKED }
    public record StatusInput(@NotNull Status status) {}
}
