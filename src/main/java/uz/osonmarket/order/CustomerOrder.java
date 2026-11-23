package uz.osonmarket.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uz.osonmarket.domain.identity.User;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}))
class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false, unique = true, length = 64)
    String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;
    UUID requestId;
    @Column(length = 512)
    String requestedFulfillment;
    @Column(length = 32)
    String phone;
    @Column(length = 64)
    String telegramAddress;
    @Column(length = 32)
    String status;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    FulfillmentMethod fulfillmentMethod;
    UUID fulfillmentBranchId;
    @Column(length = 255)
    String deliveryAddress;
    @Column(precision = 10, scale = 7)
    BigDecimal deliveryLatitude;
    @Column(precision = 10, scale = 7)
    BigDecimal deliveryLongitude;
    @Column(precision = 10, scale = 3)
    BigDecimal deliveryDistanceKm;
    @Column(precision = 14, scale = 2)
    BigDecimal deliveryPricePerKm;
    @Column(length = 1000)
    String cancellationReason;
    Instant paidAt;
    Integer serviceRating;
    @Column(length = 1000)
    String serviceReview;
    Instant ratedAt;
    @Column(name = "currency_code", length = 8)
    String currency;
    @Column(precision = 19, scale = 2)
    BigDecimal subtotal;
    @Column(precision = 19, scale = 2)
    BigDecimal discountTotal;
    @Column(precision = 19, scale = 2)
    BigDecimal shippingTotal;
    @Column(precision = 19, scale = 2)
    BigDecimal grandTotal;
    Instant placedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    List<CustomerOrderItem> items = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }
}
