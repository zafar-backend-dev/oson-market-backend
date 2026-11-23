package uz.osonmarket.catalog.price;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.domain.catalog.ProductType;
import uz.osonmarket.domain.catalog.VariantPrice;

@Component
public class CatalogPrices {
    private static final String UZS = "UZS";
    private static final String CURRENT = """
            select p from VariantPrice p
             where p.currency = 'UZS' and p.regularPrice > 0
               and (p.validFrom is null or p.validFrom <= :now)
               and (p.validTo is null or p.validTo > :now)
            """;
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    public CatalogPrices(EntityManager em, JdbcTemplate jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    public Price current(UUID id) {
        return em.createQuery(CURRENT + " and p.variant.id = :id order by p.validFrom desc nulls last, p.id desc",
                        VariantPrice.class).setParameter("now", Instant.now()).setParameter("id", id)
                .setMaxResults(1).getResultList().stream().findFirst().map(this::view).orElse(null);
    }

    public Map<UUID, Price> publicPrices() {
        Map<UUID, Price> prices = new HashMap<>();
        for (var row : em.createQuery(CURRENT + """
                 and p.variant.product.status in ('ACTIVE', 'PUBLISHED')
                 order by p.validFrom desc nulls last, p.id desc
                """, VariantPrice.class).setParameter("now", Instant.now()).getResultList()) {
            prices.putIfAbsent(row.variantId(), view(row));
        }
        return prices;
    }

    public void save(ProductType variant, BigDecimal regular, BigDecimal sale) {
        if (regular == null && sale == null) {
            return;
        }
        if (regular == null || regular.signum() <= 0
                || (sale != null && (sale.signum() <= 0 || sale.compareTo(regular) >= 0))) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "catalog.price.invalid");
        }
        Price current = current(variant.getId());
        if (current != null && same(regular, current.regularPrice()) && same(sale, current.salePrice())) {
            return;
        }
        jdbc.update("insert into currencies (code, symbol, decimal_places, enabled) values ('UZS', 'UZS', 0, true) on conflict do nothing");
        Instant now = Instant.now();
        em.createQuery("""
                update VariantPrice p set p.validTo = :now
                where p.variant.id = :id and p.currency = 'UZS'
                  and (p.validFrom is null or p.validFrom <= :now)
                  and (p.validTo is null or p.validTo > :now)
                """).setParameter("now", now).setParameter("id", variant.getId()).executeUpdate();
        em.persist(new VariantPrice(variant, UZS, regular, sale, now));
    }

    public void delete(UUID id) {
        em.createQuery("delete from VariantPrice p where p.variant.id = :id").setParameter("id", id).executeUpdate();
    }

    private boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private Price view(VariantPrice row) {
        var regular = row.regularPrice();
        var sale = row.salePrice();
        if (sale != null && (sale.signum() <= 0 || sale.compareTo(regular) >= 0)) {
            sale = null;
        }
        return new Price(UZS, regular, sale, sale == null ? regular : sale);
    }

    public record Price(String currency, BigDecimal regularPrice, BigDecimal salePrice, BigDecimal amount) {}
}
