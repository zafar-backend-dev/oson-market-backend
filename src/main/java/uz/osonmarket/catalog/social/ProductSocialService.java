package uz.osonmarket.catalog.social;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.catalog.customer.tree.CatalogTreeService;
import uz.osonmarket.domain.catalog.Product;
import uz.osonmarket.domain.identity.User;
import uz.osonmarket.domain.identity.UserProfile;
import uz.osonmarket.profile.ProfileService;

@Service
public class ProductSocialService {
    private final EntityManager em;
    private final ProfileService profiles;
    private final CatalogTreeService catalog;
    private final ProductCommentRepository comments;
    private final ProductLikeRepository productLikes;
    private final ProductCommentLikeRepository commentLikes;

    ProductSocialService(EntityManager em, CatalogTreeService catalog, ProductCommentRepository comments,
                         ProductLikeRepository productLikes, ProductCommentLikeRepository commentLikes, ProfileService profiles) {
        this.em = em;
        this.profiles = profiles;
        this.catalog = catalog;
        this.comments = comments;
        this.productLikes = productLikes;
        this.commentLikes = commentLikes;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID productId, UUID viewer) {
        requireProduct(productId, LockModeType.NONE);
        return new Summary(productLikes.countByProductId(productId),
                viewer != null && productLikes.findByProductIdAndUserId(productId, viewer).isPresent(),
                comments.countByProductIdAndDeletedAtIsNull(productId));
    }

    @Transactional(readOnly = true)
    public PagedModel<CommentView> list(UUID productId, UUID parentId, UUID viewer, Pageable pageable) {
        requireProduct(productId, LockModeType.NONE);
        var page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50),
                Sort.by("createdAt", "id"));
        if (parentId == null) return new PagedModel<>(comments.findByProductIdAndParentIsNull(productId, page)
                .map(comment -> view(comment, viewer)));
        if (!comments.existsByIdAndProductId(parentId, productId)) throw commentMissing();
        return new PagedModel<>(comments.findByProductIdAndParentId(productId, parentId, page)
                .map(comment -> view(comment, viewer)));
    }

    @Transactional
    public CommentView create(UUID productId, UUID parentId, UUID actor, String body) {
        var product = requireProduct(productId, LockModeType.NONE);
        var comment = new ProductComment();
        comment.product = product;
        comment.parent = parentId == null ? null : liveComment(productId, parentId);
        comment.author = em.getReference(User.class, actor);
        comment.body = body.strip();
        comment.createdAt = Instant.now();
        comments.save(comment);
        return view(comment, actor);
    }

    @Transactional
    public CommentView edit(UUID productId, UUID commentId, UUID actor, String body) {
        requireProduct(productId, LockModeType.NONE);
        var comment = liveComment(productId, commentId);
        requireAuthor(comment, actor);
        if (!comment.body.equals(body.strip())) {
            comment.body = body.strip();
            comment.editedAt = Instant.now();
        }
        return view(comment, actor);
    }

    @Transactional
    public void delete(UUID productId, UUID commentId, UUID actor) {
        requireProduct(productId, LockModeType.NONE);
        var comment = comments.lock(commentId, productId).orElseThrow(ProductSocialService::commentMissing);
        requireAuthor(comment, actor);
        if (comment.deletedAt == null) {
            comment.deletedAt = Instant.now();
            comment.body = "";
            commentLikes.deleteByCommentId(commentId);
        }
    }

    @Transactional
    public LikeView productReaction(UUID productId, UUID actor, boolean liked) {
        var product = requireProduct(productId, LockModeType.PESSIMISTIC_WRITE);
        var existing = productLikes.findByProductIdAndUserId(productId, actor);
        if (liked && existing.isEmpty()) {
            var reaction = new ProductLike();
            reaction.product = product;
            reaction.user = em.getReference(User.class, actor);
            productLikes.save(reaction);
        } else if (!liked) {
            existing.ifPresent(productLikes::delete);
        }
        return new LikeView(productLikes.countByProductId(productId), liked);
    }

    @Transactional
    public LikeView commentReaction(UUID productId, UUID commentId, UUID actor, boolean liked) {
        requireProduct(productId, LockModeType.NONE);
        var comment = liveComment(productId, commentId);
        var existing = commentLikes.findByCommentIdAndUserId(commentId, actor);
        if (liked && existing.isEmpty()) {
            var reaction = new ProductCommentLike();
            reaction.comment = comment;
            reaction.user = em.getReference(User.class, actor);
            commentLikes.save(reaction);
        } else if (!liked) {
            existing.ifPresent(commentLikes::delete);
        }
        return new LikeView(commentLikes.countByCommentId(commentId), liked);
    }

    @Transactional(readOnly = true)
    public byte[] commentAvatar(UUID productId, UUID commentId) {
        requireProduct(productId, LockModeType.NONE);
        var comment = em.find(ProductComment.class, commentId);
        if (comment == null || comment.deletedAt != null || !productId.equals(comment.product.getId())) {
            throw commentMissing();
        }
        return profiles.avatar(comment.author.getId());
    }

    private Product requireProduct(UUID id, LockModeType lock) {
        var product = em.find(Product.class, id, lock);
        if (product == null || !catalog.visible(product)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "catalog.notFound");
        }
        return product;
    }

    private ProductComment liveComment(UUID productId, UUID id) {
        var comment = comments.lock(id, productId).orElseThrow(ProductSocialService::commentMissing);
        if (comment.deletedAt != null) throw commentMissing();
        return comment;
    }

    private static AuthException commentMissing() {
        return new AuthException(HttpStatus.NOT_FOUND, "social.comment.notFound");
    }

    private static void requireAuthor(ProductComment comment, UUID actor) {
        if (!actor.equals(comment.author.getId())) throw new AuthException(HttpStatus.FORBIDDEN, "auth.forbidden");
    }

    private CommentView view(ProductComment comment, UUID viewer) {
        boolean deleted = comment.deletedAt != null;
        UUID parentId = comment.parent == null ? null : (UUID) em.getEntityManagerFactory()
                .getPersistenceUnitUtil().getIdentifier(comment.parent);
        var authorProfile = deleted ? null : em.find(UserProfile.class, comment.author.getId());
        String avatarUrl = authorProfile == null || authorProfile.getAvatarMedia() == null ? null
                : "/api/catalog/products/" + comment.product.getId() + "/comments/" + comment.id
                + "/avatar?v=" + authorProfile.getAvatarMedia().getId();
        return new CommentView(comment.id, parentId, deleted ? null : comment.author.getUsername(), avatarUrl,
                deleted ? null : comment.body, comment.createdAt, comment.editedAt,
                deleted, deleted ? 0 : commentLikes.countByCommentId(comment.id),
                !deleted && viewer != null && commentLikes.findByCommentIdAndUserId(comment.id, viewer).isPresent(),
                comments.countByParentId(comment.id), !deleted && viewer != null && viewer.equals(comment.author.getId()));
    }

    public record Summary(long likeCount, boolean likedByMe, long commentCount) {}
    public record LikeView(long likeCount, boolean likedByMe) {}
    public record CommentView(UUID id, UUID parentId, String authorUsername, String authorAvatarUrl, String body, Instant createdAt,
                              Instant editedAt, boolean deleted, long likeCount, boolean likedByMe,
                              long replyCount, boolean ownedByMe) {}
}
