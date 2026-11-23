package uz.osonmarket.catalog.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.osonmarket.domain.catalog.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIsNullOrderBySortOrderAscIdAsc();

    List<Category> findByParentIdOrderBySortOrderAscIdAsc(UUID parentId);

    List<Category> findByParentIsNullAndActiveTrueOrderBySortOrderAscIdAsc();

    List<Category> findByParent_IdAndActiveTrueOrderBySortOrderAscIdAsc(UUID parentId);

    boolean existsByParent_IdAndActiveTrue(UUID parentId);
}