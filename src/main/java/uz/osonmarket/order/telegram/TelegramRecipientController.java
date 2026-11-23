package uz.osonmarket.order.telegram;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders/telegram-recipients")
@SecurityRequirement(name = "bearerAuth")
public class TelegramRecipientController {
    private final TelegramOrderQueue queue;

    public TelegramRecipientController(TelegramOrderQueue queue) {
        this.queue = queue;
    }

    @GetMapping
    public List<Recipient> list() {
        return queue.recipients();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Recipient add(@Valid @RequestBody Input input) {
        return queue.add(Long.parseLong(input.chatId()), input.label());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        queue.remove(id);
    }

    public record Input(@NotBlank @Pattern(regexp = "-?[1-9][0-9]{0,15}") String chatId,
                        @Size(max = 100) String label) {}

    public record Recipient(UUID id, String chatId, String label) {}
}
