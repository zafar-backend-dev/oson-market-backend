package uz.osonmarket.domain.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_verification_codes", indexes = {
        @Index(name = "idx_auth_code_email_purpose_issued", columnList = "email, purpose, issued_at")
})
public class AuthVerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "purpose", length = 64)
    private String purpose;

    @Column(name = "code_hash", length = 255)
    private String codeHash;

    @Column(name = "attempts")
    private Integer attempts = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "pending_username", length = 64)
    private String pendingUsername;

    @Column(name = "pending_password_hash", length = 255)
    private String pendingPasswordHash;

    @Column(name = "pending_first_name", length = 100)
    private String pendingFirstName;

    @Column(name = "pending_last_name", length = 100)
    private String pendingLastName;

    @Column(name = "credential_version")
    private Integer credentialVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getPendingUsername() {
        return pendingUsername;
    }

    public void setPendingUsername(String pendingUsername) {
        this.pendingUsername = pendingUsername;
    }

    public String getPendingPasswordHash() {
        return pendingPasswordHash;
    }

    public void setPendingPasswordHash(String pendingPasswordHash) {
        this.pendingPasswordHash = pendingPasswordHash;
    }

    public Integer getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Integer credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public String getPendingFirstName() {
        return pendingFirstName;
    }

    public void setPendingFirstName(String pendingFirstName) {
        this.pendingFirstName = pendingFirstName;
    }

    public String getPendingLastName() {
        return pendingLastName;
    }

    public void setPendingLastName(String pendingLastName) {
        this.pendingLastName = pendingLastName;
    }
}
