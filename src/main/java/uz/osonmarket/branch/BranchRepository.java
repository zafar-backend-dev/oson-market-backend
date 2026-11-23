package uz.osonmarket.branch;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BranchRepository extends JpaRepository<Branch, UUID> {
    org.springframework.data.domain.Page<Branch> findByStatus(String status, org.springframework.data.domain.Pageable page);
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, UUID id);
}
