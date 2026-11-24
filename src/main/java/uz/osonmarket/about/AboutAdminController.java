package uz.osonmarket.about;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/site/about")
@SecurityRequirement(name = "bearerAuth")
public class AboutAdminController {
    private final AboutService about;

    public AboutAdminController(AboutService about) {
        this.about = about;
    }

    @GetMapping
    public View get() {
        return about.get();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public View save(@Valid @RequestBody Input input) {
        return about.save(input);
    }

    public record Input(@NotNull @Valid Translation uz, @NotNull @Valid Translation ru,
                        Map<Platform, @NotBlank @Size(max = 2048) @URL(protocol = "https") String> socialLinks) {}

    public record Translation(@NotBlank @Size(max = 200) String title,
                              @NotBlank @Size(max = 10000) String description) {}

    public enum Platform { TELEGRAM, INSTAGRAM, FACEBOOK, YOUTUBE, TIKTOK, X, LINKEDIN, WHATSAPP }

    public record View(Translation uz, Translation ru, Instant updatedAt, Map<Platform, String> socialLinks) {}
}
