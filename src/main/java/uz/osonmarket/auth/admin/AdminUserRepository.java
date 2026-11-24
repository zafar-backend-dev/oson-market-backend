package uz.osonmarket.auth.admin;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uz.osonmarket.domain.identity.User;

interface AdminUserRepository extends Repository<User, UUID> {
    String FROM = """
            from User u
            left join UserProfile p on p.userId = u.id
            left join u.preferredLanguage lang
            """;
    String SELECT = """
            select new uz.osonmarket.auth.admin.AdminUserView(
                u.id, u.username, u.email, u.role, u.status, p.firstName, p.lastName, p.phone,
                lang.code, u.emailVerifiedAt, u.createdAt, u.updatedAt)
            """ + FROM;
    String FILTERS = """
            where (:role = '' or u.role = :role)
              and (:status = '' or u.status = :status)
              and (:query = '' or lower(u.email) like :pattern escape '!'
                  or lower(u.username) like :pattern escape '!'
                  or lower(concat(concat(coalesce(p.firstName, ''), ' '), coalesce(p.lastName, ''))) like :pattern escape '!'
                  or p.phone like :pattern escape '!')
            """;

    @Query(value = SELECT + FILTERS + " order by u.createdAt desc, u.id desc",
            countQuery = "select count(u) " + FROM + FILTERS)
    Page<AdminUserView> search(String role, String status, String query, String pattern, Pageable pageable);

    @Query(SELECT + " where u.id = :id")
    Optional<AdminUserView> findView(UUID id);
}
