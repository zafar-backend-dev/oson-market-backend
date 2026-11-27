package uz.osonmarket.auth.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthRequests {
    private AuthRequests() {}

    public record SignIn(@NotBlank @Size(max = 255) String login,
                         @NotBlank @Size(max = 72) String password,
                         @NotNull @AssertTrue Boolean termsAccepted,
                         @NotNull @AssertTrue Boolean privacyAcknowledged) {}

    public record SignUp(@NotBlank @Size(max = 100) String firstName,
                         @NotBlank @Size(max = 100) String lastName,
                         @NotBlank @Email @Size(max = 255) String email,
                         @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,64}") String username,
                         @NotBlank @Size(min = 8, max = 72) String password,
                         @NotNull @AssertTrue Boolean termsAccepted,
                         @NotNull @AssertTrue Boolean privacyAcknowledged) {}

    public record Verify(@NotNull UUID challengeId,
                         @NotNull @Pattern(regexp = "[0-9]{6}") String code) {}

    public record ForgotPassword(@NotBlank @Email @Size(max = 255) String email) {}

    public record ResetPassword(@NotNull UUID challengeId,
                                 @NotNull @Pattern(regexp = "[0-9]{6}") String code,
                                 @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
