package uz.osonmarket.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.time.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.auth.service.PasswordAuthService;

@RestController
@RequestMapping("/api/admin/backups")
public class BackupAdminController {
    private final BackupAdminService backups;
    private final PasswordAuthService auth;

    public BackupAdminController(BackupAdminService backups, PasswordAuthService auth) {
        this.backups = backups;
        this.auth = auth;
    }

    @GetMapping
    public ResponseEntity<Dashboard> dashboard() throws IOException {
        var settings = backups.settings();
        var archives = backups.archives();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var next = now.toLocalDate().atTime(settings.time());
        if (now.toLocalDate().equals(settings.lastScheduledDate())) next = next.plusDays(1);
        var latest = archives.stream().filter(item -> item.integrity().equals("DONE")).findFirst().orElse(null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Dashboard(backups.configured(),
                settings, settings.enabled() && backups.configured() ? next.toInstant(ZoneOffset.UTC) : null,
                latest == null ? null : latest.modifiedAt(), archives.size(),
                archives.stream().mapToLong(BackupAdminService.Archive::size).sum(), backups.freeBytes(),
                backups.ledgerConfigured(), backups.lastCheckpointStatus(), backups.offsiteStatus(latest), archives, backups.events()));
    }

    @PostMapping("/otp")
    public ResponseEntity<Challenge> otp(@AuthenticationPrincipal Jwt jwt, Locale locale) {
        UUID actor = actor(jwt);
        UUID challenge = auth.requestBackupAdminCode(actor, locale);
        backups.audit(actor, "OTP_REQUEST", null);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Challenge(challenge));
    }

    @PostMapping("/jobs")
    public ResponseEntity<Job> enqueue(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JobInput input) {
        UUID actor = actor(jwt);
        authorize(actor, input.otp());
        return ResponseEntity.accepted().body(new Job(backups.enqueue(actor, input.operation(), input.archive())));
    }

    @PostMapping("/settings")
    public ResponseEntity<Void> settings(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SettingsInput input) {
        UUID actor = actor(jwt);
        authorize(actor, input.otp());
        backups.configure(actor, input.enabled(), input.time(), input.retentionDays());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/download")
    public ResponseEntity<FileSystemResource> download(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DownloadInput input)
            throws IOException {
        UUID actor = actor(jwt);
        authorize(actor, input.otp());
        var archive = backups.archive(input.archive());
        if (!backups.verifiedArchive(archive)) {
            throw new AuthException(HttpStatus.CONFLICT, "backup.archive.unverified");
        }
        backups.audit(actor, "DOWNLOAD", input.archive());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(input.archive()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(archive)).body(new FileSystemResource(archive));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private void authorize(UUID actor, Otp otp) {
        auth.verifyBackupAdminCode(actor, otp.challengeId(), otp.code());
    }

    public record Challenge(UUID challengeId) {}
    public record Job(UUID id) {}
    public record Otp(@NotNull UUID challengeId, @NotNull @Pattern(regexp = "[0-9]{6}") String code) {}
    public record JobInput(@NotNull BackupAdminService.Operation operation, String archive, @NotNull @Valid Otp otp) {}
    public record SettingsInput(boolean enabled, @NotNull LocalTime time, int retentionDays, @NotNull @Valid Otp otp) {}
    public record DownloadInput(@NotNull String archive, @NotNull @Valid Otp otp) {}
    public record Dashboard(boolean configured, BackupAdminService.Settings settings, Instant nextBackup,
                            Instant lastBackup, int archiveCount, long totalBytes, Long freeBytes,
                            boolean ledgerConfigured, String checkpointStatus, String offsiteStatus, List<BackupAdminService.Archive> archives,
                            List<BackupAdminService.Event> events) {}
}
