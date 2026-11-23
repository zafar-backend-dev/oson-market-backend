package uz.osonmarket.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import uz.osonmarket.domain.catalog.ProductType;

@Entity
@Table(name = "order_items")
class CustomerOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    CustomerOrder order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    ProductType variant;
    @Column(length = 100)
    String skuSnapshot;
    @Column(length = 255)
    String titleUzSnapshot;
    @Column(length = 255)
    String titleRuSnapshot;
    Integer quantity;
    @Column(precision = 19, scale = 2)
    BigDecimal unitPrice;
    @Column(precision = 19, scale = 2)
    BigDecimal discountAmount;
    @Column(precision = 19, scale = 2)
    BigDecimal lineTotal;
}
