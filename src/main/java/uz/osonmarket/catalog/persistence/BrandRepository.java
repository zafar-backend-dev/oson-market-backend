package uz.osonmarket.catalog.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.osonmarket.domain.catalog.Brand;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    @Query("""
            select b from Brand b
            where (:active is null or b.active = :active)
              and (:term = '' or locate(:term, lower(b.name)) > 0 or locate(:term, lower(b.slug)) > 0)
            """)
    Page<Brand> search(@Param("term") String term, @Param("active") Boolean active, Pageable pageable);
}
