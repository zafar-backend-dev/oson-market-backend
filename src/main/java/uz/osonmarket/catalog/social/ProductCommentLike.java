package uz.osonmarket.catalog.social;

import jakarta.persistence.*;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import uz.osonmarket.domain.identity.User;

@Entity
@Table(name = "product_comment_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"comment_id", "user_id"}))
class ProductCommentLike {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    ProductComment comment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    User user;
}
