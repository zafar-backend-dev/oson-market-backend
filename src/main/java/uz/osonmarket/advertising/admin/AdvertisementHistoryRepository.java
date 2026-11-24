package uz.osonmarket.advertising.admin;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdvertisementHistoryRepository extends JpaRepository<AdvertisementHistory, UUID> {
    Page<AdvertisementHistory> findByAdvertisementIdOrderByRevisionDesc(UUID advertisementId, Pageable pageable);
}
