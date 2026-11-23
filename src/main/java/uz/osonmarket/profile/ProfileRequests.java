package uz.osonmarket.profile;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class ProfileRequests {
    private ProfileRequests() {}

    public record Update(@Size(max = 100) String firstName,
                         @Size(max = 100) String lastName,
                         @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{5,31}$") String phone) {}

    public record Username(@NotNull @Pattern(regexp = "[a-zA-Z0-9_]{3,64}") String username) {}
    public record Language(@NotNull @Pattern(regexp = "uz|ru") String language) {}
    public record Password(@NotBlank @Size(max = 72) String currentPassword,
                           @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record EmailChange(@NotBlank @Email @Size(max = 255) String email,
                        @NotBlank @Size(max = 72) String currentPassword) {}
    public record VerifyEmail(@NotNull UUID challengeId,
                              @NotNull @Pattern(regexp = "[0-9]{6}") String code) {}
}
