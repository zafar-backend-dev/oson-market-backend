package uz.osonmarket.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mobile/chat")
class ChatController {
    private final ChatService chat;
    private final List<String> origins;
    private final List<String> webOrigins;

    ChatController(ChatService chat, @Value("${app.mobile-frontend-origins}") List<String> origins,
                   @Value("${app.cors-origin}") String webOrigin,
                   @Value("${app.local-frontend-origin}") String localOrigin) {
        this.chat = chat;
        this.origins = origins;
        this.webOrigins = List.of(webOrigin, localOrigin);
    }

    private ChatService.Actor actor(Jwt jwt, String origin) {
        var actor = chat.actor(UUID.fromString(jwt.getSubject()));
        if (origin == null || !(origins.contains(origin) || webOrigins.contains(origin))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return actor;
    }

    @GetMapping("/conversations")
    List<ChatService.Conversation> conversations(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestParam(defaultValue = "9223372036854775807") long beforeId,
            @RequestParam(defaultValue = "30") int limit) {
        return chat.conversations(actor(jwt, origin), beforeId, pageSize(limit));
    }

    @GetMapping("/messages")
    List<ChatService.Message> messages(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestParam(required = false) UUID userId, @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId, @RequestParam(defaultValue = "30") int limit) {
        if (beforeId != null && afterId != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long cursor = afterId != null ? afterId : beforeId == null ? Long.MAX_VALUE : beforeId;
        return chat.history(actor(jwt, origin), userId, cursor, afterId != null, pageSize(limit));
    }

    @GetMapping("/unread-count")
    java.util.Map<String, Long> unread(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestParam(required = false) UUID userId) {
        return java.util.Map.of("count", chat.unread(actor(jwt, origin), userId));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ChatService.Message upload(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestParam(required = false) UUID userId, @RequestParam UUID requestId,
            @RequestParam String kind, @RequestParam(required = false) Long replyToId,
            @RequestParam MultipartFile file) throws IOException {
        var actor = actor(jwt, origin);
        chat.owner(actor, userId);
        return chat.send(actor, userId, requestId, null, ChatMedia.read(file, kind), replyToId);
    }

    @GetMapping("/attachments/{messageId}")
    ResponseEntity<byte[]> attachment(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Origin", required = false) String origin, @PathVariable long messageId) {
        var media = chat.attachment(actor(jwt, origin), messageId);
        var contentType = "image/x-chat-original".equals(media.type())
                ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(media.type());
        return ResponseEntity.ok().contentType(contentType)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(media.name(), StandardCharsets.UTF_8).build().toString())
                .body(media.bytes());
    }

    private int pageSize(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }
}
