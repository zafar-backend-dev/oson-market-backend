package uz.osonmarket.domain.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {
    @Id
    private UUID accountId;
    private Instant verifiedAt;
    @jakarta.persistence.Column(unique = true)
    private UUID receipt;

    public UUID ensureReceipt() {
        if (receipt == null) receipt = UUID.randomUUID();
        return receipt;
    }

    protected AccountDeletionRequest() {}

    public AccountDeletionRequest(UUID accountId, Instant verifiedAt) {
        this.accountId = accountId;
        this.verifiedAt = verifiedAt;
    }

    public UUID getAccountId() { return accountId; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
