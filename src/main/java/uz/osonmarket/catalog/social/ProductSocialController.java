package uz.osonmarket.catalog.social;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/products/{productId}")
public class ProductSocialController {
    private final ProductSocialService social;

    public ProductSocialController(ProductSocialService social) {
        this.social = social;
    }

    @GetMapping("/social")
    public ProductSocialService.Summary summary(@PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt) {
        return social.summary(productId, viewer(jwt));
    }

    @GetMapping("/comments")
    public PagedModel<ProductSocialService.CommentView> comments(@PathVariable UUID productId,
            @AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return social.list(productId, null, viewer(jwt), pageable);
    }

    @GetMapping("/comments/{commentId}/replies")
    public PagedModel<ProductSocialService.CommentView> replies(@PathVariable UUID productId, @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return social.list(productId, commentId, viewer(jwt), pageable);
    }

    @GetMapping(value = "/comments/{commentId}/avatar", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> avatar(@PathVariable UUID productId, @PathVariable UUID commentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(social.commentAvatar(productId, commentId));
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSocialService.CommentView comment(@PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommentInput input) {
        return social.create(productId, null, viewer(jwt), input.body());
    }

    @PostMapping("/comments/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSocialService.CommentView reply(@PathVariable UUID productId, @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CommentInput input) {
        return social.create(productId, commentId, viewer(jwt), input.body());
    }

    @PatchMapping("/comments/{commentId}")
    public ProductSocialService.CommentView edit(@PathVariable UUID productId, @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CommentInput input) {
        return social.edit(productId, commentId, viewer(jwt), input.body());
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID productId, @PathVariable UUID commentId, @AuthenticationPrincipal Jwt jwt) {
        social.delete(productId, commentId, viewer(jwt));
    }

    @PutMapping("/like")
    public ProductSocialService.LikeView like(@PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt) {
        return social.productReaction(productId, viewer(jwt), true);
    }

    @DeleteMapping("/like")
    public ProductSocialService.LikeView unlike(@PathVariable UUID productId, @AuthenticationPrincipal Jwt jwt) {
        return social.productReaction(productId, viewer(jwt), false);
    }

    @PutMapping("/comments/{commentId}/like")
    public ProductSocialService.LikeView likeComment(@PathVariable UUID productId, @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt) {
        return social.commentReaction(productId, commentId, viewer(jwt), true);
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ProductSocialService.LikeView unlikeComment(@PathVariable UUID productId, @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt) {
        return social.commentReaction(productId, commentId, viewer(jwt), false);
    }

    private static UUID viewer(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }

    public record CommentInput(@NotBlank @Size(max = 2000) String body) {}
}
