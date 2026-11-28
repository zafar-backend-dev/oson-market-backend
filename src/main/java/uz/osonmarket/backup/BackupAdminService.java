package uz.osonmarket.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.recovery.DeletionLedger;

@Service
public class BackupAdminService {
    private static final Pattern ARCHIVE = Pattern.compile("osonmarket-[0-9]{8}T[0-9]{6}Z-[A-Za-z0-9]{8}\\.dump\\.gpg");
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final DeletionLedger ledger;
    private final String checkpoint;
    private final Path directory;
    private final Path script;
    private final Path offsiteReceipts;
    private final boolean workerEnabled;
    private final boolean applyRotation;
    private final boolean configured;
    private final AtomicBoolean ticking = new AtomicBoolean();

    public BackupAdminService(JdbcTemplate db, TransactionTemplate transactions, DeletionLedger ledger,
                              @Value("${app.deletion-ledger.restore-sha256:}") String checkpoint,
                              @Value("${app.backup-admin.script:}") String script,
                              @Value("${OSON_BACKUP_DIR:}") String directory,
                              @Value("${app.backup-admin.offsite-receipts:}") String offsiteReceipts,
                              @Value("${app.backup-admin.worker-enabled:true}") boolean workerEnabled,
                              @Value("${app.backup-admin.apply-rotation:false}") boolean applyRotation) {
        this.db = db;
        this.offsiteReceipts = offsiteReceipts.isBlank() ? null : Path.of(offsiteReceipts);
        this.workerEnabled = workerEnabled;
        this.applyRotation = applyRotation;
        this.transactions = transactions;
        this.ledger = ledger;
        this.checkpoint = checkpoint;
        this.directory = directory.isBlank() ? null : Path.of(directory);
        this.script = script.isBlank() ? null : Path.of(script);
        this.configured = this.directory != null && this.directory.isAbsolute() && this.script != null
                && this.script.isAbsolute() && Files.isRegularFile(this.script, LinkOption.NOFOLLOW_LINKS);
    }

    public enum Operation { CREATE, VERIFY, ROTATION_PREVIEW, RESTORE_CHECK }

    public record Settings(boolean enabled, LocalTime time, int retentionDays, LocalDate lastScheduledDate) {}
    public record Archive(String name, long size, Instant modifiedAt, String integrity) {}
    public record Event(UUID id, UUID actor, String operation, String archive, String state,
                        Instant createdAt, Instant finishedAt, String result) {}

    public Settings settings() {
        return db.queryForObject("select * from backup_admin_settings where singleton", (rs, row) ->
                new Settings(rs.getBoolean("enabled"), rs.getObject("utc_time", LocalTime.class),
                        rs.getInt("retention_days"), rs.getObject("last_scheduled_date", LocalDate.class)));
    }

    public List<Event> events() {
        return db.query("select * from backup_admin_jobs order by created_at desc limit 100", (rs, row) ->
                new Event(rs.getObject("id", UUID.class), rs.getObject("actor_id", UUID.class),
                        rs.getString("operation"), rs.getString("archive"), rs.getString("state"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
                        rs.getString("result")));
    }

    private boolean directoryAvailable() {
        if (!configured || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            if (!directory.getFileSystem().supportedFileAttributeViews().contains("posix")) return !workerEnabled;
            Path real = directory.toRealPath();
            if (List.of("/var/www", "/srv/www", "/usr/share/nginx/html").stream().map(Path::of).anyMatch(real::startsWith)) {
                return false;
            }
            return Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                    .equals(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        } catch (IOException failure) {
            return false;
        }
    }

    public Path archive(String name) {
        if (name == null || !ARCHIVE.matcher(name).matches() || !directoryAvailable()) {
            throw new AuthException(HttpStatus.NOT_FOUND, "backup.archive.not_found");
        }
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "backup.archive.not_found");
        }
        return file;
    }

    public List<Archive> archives() throws IOException {
        if (!directoryAvailable()) return List.of();
        var checks = db.query("""
                select distinct on (archive) archive, state from backup_admin_jobs
                where archive is not null and operation in ('CREATE','VERIFY') and state in ('DONE','FAILED')
                order by archive, finished_at desc
                """, rs -> {
            Map<String, String> result = new HashMap<>();
            while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            return result;
        });
        List<Archive> result = new ArrayList<>();
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!ARCHIVE.matcher(name).matches() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                result.add(new Archive(name, Files.size(file), Files.getLastModifiedTime(file).toInstant(),
                        checks.getOrDefault(name, "UNKNOWN")));
            }
        }
        result.sort(Comparator.comparing(Archive::modifiedAt).reversed());
        return result;
    }

    public boolean verifiedArchive(Path archive) throws IOException {
        var checks = db.queryForList("""
                select archive_sha256 from backup_admin_jobs
                where archive=? and operation in ('CREATE','VERIFY') and state in ('DONE','FAILED')
                order by finished_at desc limit 1
                """, String.class, archive.getFileName().toString());
        return !checks.isEmpty() && checks.get(0) != null && checks.get(0).equals(digest(archive));
    }

    private String digest(Path archive) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS);
                 var stream = new java.security.DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public Long freeBytes() throws IOException {
        return directoryAvailable() ? Files.getFileStore(directory).getUsableSpace() : null;
    }

    public String checkpointStatus() {
        if (!ledger.configured() || checkpoint.isBlank()) return "UNCONFIGURED";
        try {
            ledger.verifyCheckpoint(checkpoint);
            return "VERIFIED";
        } catch (RuntimeException failure) {
            return "FAILED";
        }
    }

    public String lastCheckpointStatus() {
        if (!ledger.configured() || checkpoint.isBlank()) return "UNCONFIGURED";
        var checks = db.queryForList("""
                select case when result='CHECKPOINT_FAILED' then 'FAILED' else 'VERIFIED' end
                from backup_admin_jobs where operation='RESTORE_CHECK'
                    and (result='CHECKPOINT_FAILED' or state='DONE')
                order by finished_at desc limit 1
                """, String.class);
        return checks.isEmpty() ? "UNKNOWN" : checks.get(0);
    }

    public String offsiteStatus(Archive latest) {
        if (offsiteReceipts == null || !offsiteReceipts.isAbsolute()) return "UNCONFIGURED";
        if (latest == null) return "UNKNOWN";
        Path receipt = offsiteReceipts.resolve(latest.name() + ".sha256");
        try {
            if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS) || Files.size(receipt) > 128) {
                return "UNKNOWN";
            }
            var checks = db.queryForList("""
                    select archive_sha256 from backup_admin_jobs where archive=? and archive_sha256 is not null
                    order by finished_at desc limit 1
                    """, String.class, latest.name());
            if (checks.isEmpty()) return "UNKNOWN";
            return checks.get(0).equals(Files.readString(receipt).strip()) ? "VERIFIED" : "FAILED";
        } catch (IOException failure) {
            return "FAILED";
        }
    }

    public boolean configured() {
        return workerEnabled && Files.isExecutable(Path.of("/bin/bash")) && directoryAvailable();
    }
    public boolean ledgerConfigured() { return ledger.configured(); }

    public void configure(UUID actor, boolean enabled, LocalTime time, int retention) {
        if (time == null || retention < 7 || retention > 90) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "backup.settings.invalid");
        }
        if (enabled && !configured()) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "backup.unavailable");
        transactions.executeWithoutResult(status -> {
            db.update("update backup_admin_settings set enabled=?, utc_time=?, retention_days=? where singleton",
                    enabled, time, retention);
            audit(actor, "SETTINGS", null);
        });
    }

    public void audit(UUID actor, String operation, String archive) {
        db.update("""
                insert into backup_admin_jobs (id, actor_id, operation, archive, state, finished_at)
                values (?, ?, ?, ?, 'DONE', now())
                """, UUID.randomUUID(), actor, operation, archive);
    }

    public UUID enqueue(UUID actor, Operation operation, String archive) {
        if (!configured()) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "backup.unavailable");
        if (operation == Operation.VERIFY || operation == Operation.RESTORE_CHECK) archive(archive);
        else if (archive != null) throw new AuthException(HttpStatus.BAD_REQUEST, "backup.settings.invalid");
        UUID id = UUID.randomUUID();
        int inserted = db.update("""
                insert into backup_admin_jobs (id, actor_id, operation, archive, state)
                values (?, ?, ?, ?, 'QUEUED') on conflict do nothing
                """, id, actor, operation.name(), archive);
        if (inserted != 1) throw new AuthException(HttpStatus.CONFLICT, "backup.job.active");
        return id;
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 5000)
    public void poll() {
        if (!configured() || !ticking.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                runWorker();
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(BackupAdminService.class).warn("Backup worker database operation failed");
            } finally {
                ticking.set(false);
            }
        }, "backup-admin-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void runWorker() {
        db.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement();
                 var lock = statement.executeQuery("select pg_try_advisory_lock(421320241)")) {
                if (!lock.next() || !lock.getBoolean(1)) return null;
            }
            try {
                db.update("""
                        update backup_admin_jobs set state='FAILED', result='INTERRUPTED', finished_at=now()
                        where state='RUNNING'
                        """);
                schedule();
                List<Event> queue = db.query("""
                        update backup_admin_jobs set state='RUNNING' where state='QUEUED'
                        returning id, operation, archive
                        """, (rs, row) -> new Event(rs.getObject(1, UUID.class), null, rs.getString(2),
                        rs.getString(3), "RUNNING", null, null, null));
                if (!queue.isEmpty()) execute(queue.get(0));
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("select pg_advisory_unlock(421320241)");
                }
            }
            return null;
        });
    }

    private void schedule() {
        transactions.executeWithoutResult(status -> {
            Settings settings = settings();
            var now = LocalDateTime.now(ZoneOffset.UTC);
            if (!settings.enabled() || now.toLocalTime().isBefore(settings.time())
                    || now.toLocalDate().equals(settings.lastScheduledDate())) return;
            try {
                enqueue(null, Operation.CREATE, null);
                db.update("update backup_admin_settings set last_scheduled_date=? where singleton", now.toLocalDate());
            } catch (AuthException busy) {
                if (busy.status() != HttpStatus.CONFLICT) throw busy;
            }
        });
    }

    private void execute(Event job) {
        String name = job.archive();
        String state = "FAILED";
        String result = "OPERATION_FAILED";
        String hash = null;
        Path output = null;
        Process process = null;
        try {
            Operation operation = Operation.valueOf(job.operation());
            if (operation == Operation.RESTORE_CHECK && !"VERIFIED".equals(checkpointStatus())) {
                result = "CHECKPOINT_FAILED";
                return;
            }
            if (name != null) archive(name);
            output = Files.createTempFile(directory, ".admin-job-", ".log");
            var command = new ProcessBuilder("/bin/bash", script.toString(), operation.name(), name == null ? "" : name);
            var environment = command.environment();
            environment.clear();
            for (String key : List.of("HOME", "GNUPGHOME", "PGHOST", "PGPORT", "PGUSER", "PGDATABASE",
                    "PGPASSFILE", "PGSSLMODE", "PGSSLROOTCERT", "OSON_BACKUP_PASSPHRASE")) {
                String value = System.getenv(key);
                if (value != null) environment.put(key, value);
            }
            environment.put("PATH", "/usr/bin:/bin");
            environment.put("OSON_BACKUP_DIR", directory.toString());
            command.environment().put("OSON_BACKUP_RETENTION_DAYS", Integer.toString(settings().retentionDays()));
            command.environment().put("OSON_BACKUP_ROTATION_DRY_RUN", Boolean.toString(!applyRotation));
            command.redirectError(ProcessBuilder.Redirect.DISCARD);
            command.redirectOutput(output.toFile());
            process = command.start();
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                result = "TIMED_OUT";
                return;
            }
            if (process.exitValue() != 0 || Files.size(output) > 4096) {
                if (operation == Operation.VERIFY || operation == Operation.RESTORE_CHECK) result = "ARCHIVE_CHECK_FAILED";
                return;
            }
            if (operation == Operation.RESTORE_CHECK && !"VERIFIED".equals(checkpointStatus())) {
                result = "CHECKPOINT_FAILED";
                return;
            }
            String summary = Files.readString(output).strip();
            if (operation == Operation.CREATE) {
                var match = Pattern.compile("(?m)^Verified backup published: (.+)$").matcher(summary);
                if (!match.find()) return;
                name = match.group(1);
                archive(name);
            }
            if (operation == Operation.ROTATION_PREVIEW) {
                if (!summary.matches("[0-9]+")) return;
                result = summary;
            } else {
                result = "VERIFIED";
            }
            if (operation == Operation.CREATE || operation == Operation.VERIFY) hash = digest(archive(name));
            state = "DONE";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = "INTERRUPTED";
        } catch (IOException | AuthException failure) {
            result = "OPERATION_FAILED";
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            if (output != null) {
                try { Files.deleteIfExists(output); } catch (IOException ignored) { /* private directory */ }
            }
            db.update("update backup_admin_jobs set state=?, result=?, archive=?, archive_sha256=?, finished_at=now() where id=?",
                    state, result, name, hash, job.id());
        }
    }
}
