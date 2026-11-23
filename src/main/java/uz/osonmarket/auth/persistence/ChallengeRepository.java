package uz.osonmarket.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.osonmarket.domain.identity.AuthVerificationCode;

public interface ChallengeRepository extends JpaRepository<AuthVerificationCode, UUID> {
    Optional<AuthVerificationCode> findFirstByEmailAndPurposeAndIssuedAtIsNotNullOrderByIssuedAtDesc(String email, String purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AuthVerificationCode c where c.id = :id")
    Optional<AuthVerificationCode> findForUpdate(@Param("id") UUID id);
}
