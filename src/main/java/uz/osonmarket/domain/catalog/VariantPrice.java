package uz.osonmarket.domain.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "variant_prices")
public class VariantPrice {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductType variant;
    @Column(name = "currency_code", length = 8)
    private String currency;
    @Column(precision = 19, scale = 2)
    private BigDecimal regularPrice;
    @Column(precision = 19, scale = 2)
    private BigDecimal salePrice;
    private Instant validFrom;
    private Instant validTo;

    protected VariantPrice() {}

    public VariantPrice(ProductType variant, String currency, BigDecimal regularPrice, BigDecimal salePrice, Instant now) {
        this.variant = variant;
        this.currency = currency;
        this.regularPrice = regularPrice;
        this.salePrice = salePrice;
        this.validFrom = now;
    }

    public UUID variantId() { return variant.getId(); }
    public BigDecimal regularPrice() { return regularPrice; }
    public BigDecimal salePrice() { return salePrice; }
    public void close(Instant now) { validTo = now; }
}
