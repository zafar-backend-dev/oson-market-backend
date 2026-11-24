package uz.osonmarket.advertising.admin;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AdvertisementDispatchRepository extends JpaRepository<AdvertisementDispatch, UUID> {
    @Query(value = """
            SELECT * FROM advertisement_dispatches WHERE next_push_at <= :now
            ORDER BY next_push_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AdvertisementDispatch> lockNextPush(Instant now);
    @Query("select d from AdvertisementDispatch d where d.advertisementId = :id and d.revision = :revision")
    Optional<AdvertisementDispatch> revision(UUID id, long revision);

    Optional<AdvertisementDispatch> findByAdvertisementIdAndRequestedRevision(UUID id, long requestedRevision);

    Page<AdvertisementDispatch> findByAdvertisementIdOrderBySentAtDescIdDesc(UUID advertisementId, Pageable pageable);

    String CHANNEL = " and ((:web = true and d.webEnabled = true) or (:web = false and d.appEnabled = true))";
    String INBOX = " from AdvertisementDispatch d where d.availableAt <= :now" + CHANNEL;

    @Query("select d" + INBOX + " order by d.availableAt desc, d.id desc")
    Page<AdvertisementDispatch> inbox(boolean web, Instant now, Pageable pageable);

    @Query("select count(d)" + INBOX + """
            and not exists (select 1 from AdvertisementNotificationRead r
                            where r.dispatchId = d.id and r.userId = :userId)
            """)
    long unread(boolean web, Instant now, UUID userId);

    String LIVE = """
             from AdvertisementDispatch d join Advertisement a on a.id = d.advertisementId
             where a.status = uz.osonmarket.advertising.admin.AdvertisementModel.Status.PUBLISHED
               and d.availableAt <= :now and (a.startsAt is null or a.startsAt <= :now)
               and (a.endsAt is null or a.endsAt > :now)
               and d.revision = (select max(previous.revision) from AdvertisementDispatch previous
                                 where previous.advertisementId = a.id)
            """ + CHANNEL + " and ((:web = true and a.webEnabled = true) or (:web = false and a.appEnabled = true))";

    @Query("select d" + LIVE + " order by a.sortOrder, d.availableAt desc, d.id")
    Page<AdvertisementDispatch> live(boolean web, Instant now, Pageable pageable);

    @Query("select d" + LIVE + " and d.id = :id")
    Optional<AdvertisementDispatch> liveById(boolean web, Instant now, UUID id);
}
