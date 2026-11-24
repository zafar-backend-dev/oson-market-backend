package uz.osonmarket.advertising.admin;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.osonmarket.advertising.admin.AdvertisementModel.Status;

interface AdvertisementRepository extends JpaRepository<Advertisement, UUID> {
    @Query("""
            select a from Advertisement a
            where (:status is null or a.status = :status)
              and (:web = false or a.webEnabled = true)
              and (:app = false or a.appEnabled = true)
              and (lower(a.titleUz) like :pattern escape '!' or lower(a.titleRu) like :pattern escape '!')
            order by a.updatedAt desc, a.id desc
            """)
    Page<Advertisement> search(Status status, boolean web, boolean app, String pattern, Pageable pageable);
}
