package uz.osonmarket.catalog.social;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import uz.osonmarket.domain.catalog.Product;
import uz.osonmarket.domain.identity.User;

@Entity
@Table(name = "product_comments", indexes = @Index(columnList = "product_id, parent_id, created_at"))
class ProductComment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    User author;
    @ManyToOne(fetch = FetchType.LAZY)
    ProductComment parent;
    @Column(nullable = false, length = 2000)
    String body;
    @Column(nullable = false)
    Instant createdAt;
    Instant editedAt;
    Instant deletedAt;
}
