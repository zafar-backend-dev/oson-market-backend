package uz.osonmarket.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeletionLedger {
    private final String url;
    private final String databaseUser;
    private final String password;

    public DeletionLedger(@Value("${app.deletion-ledger.url:}") String url,
                          @Value("${app.deletion-ledger.user:}") String databaseUser,
                          @Value("${app.deletion-ledger.password:}") String password,
                          @Value("${spring.datasource.url}") String applicationUrl) {
        if (!url.isBlank() && (!url.startsWith("jdbc:postgresql://")
                || bareUrl(url).equals(bareUrl(applicationUrl)))) {
            throw new IllegalArgumentException("Deletion ledger requires a separate PostgreSQL database");
        }
        this.url = url;
        this.databaseUser = databaseUser;
        this.password = password;
    }

    private static String bareUrl(String value) {
        return value.split("\\?", 2)[0];
    }

    public boolean configured() {
        return !url.isBlank();
    }

    private Connection connect() throws SQLException {
        var properties = new java.util.Properties();
        properties.setProperty("user", databaseUser);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        return DriverManager.getConnection(url, properties);
    }

    public void record(UUID id) {
        if (!configured()) return;
        try (var connection = connect()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SET LOCAL synchronous_commit = on");
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO public.oson_erasure_ledger(account_id) VALUES (?) ON CONFLICT DO NOTHING
                    """)) {
                statement.setObject(1, id);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException failure) {
            throw unavailable();
        }
    }

    public boolean contains(UUID id) {
        if (!configured()) return false;
        try (var connection = connect();
             var statement = connection.prepareStatement("SELECT 1 FROM public.oson_erasure_ledger WHERE account_id = ?")) {
            statement.setObject(1, id);
            try (var cursor = statement.executeQuery()) {
                return cursor.next();
            }
        } catch (SQLException failure) {
            throw unavailable();
        }
    }

    public void verifyCheckpoint(String expectedHash) {
        readCheckpoint(expectedHash);
    }

    List<UUID> readCheckpoint(String expectedHash) {
        if (!configured() || !expectedHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("Recovery requires an independently verified ledger checkpoint");
        }
        try (var connection = connect();
             var statement = connection.prepareStatement("SELECT account_id FROM public.oson_erasure_ledger ORDER BY account_id");
             var cursor = statement.executeQuery()) {
            var ids = new ArrayList<UUID>();
            var hash = MessageDigest.getInstance("SHA-256");
            while (cursor.next()) {
                var id = cursor.getObject(1, UUID.class);
                ids.add(id);
                hash.update((id + "\n").getBytes(StandardCharsets.US_ASCII));
            }
            if (!expectedHash.equals(HexFormat.of().formatHex(hash.digest()))) {
                throw new IllegalStateException("Deletion ledger does not match verified checkpoint");
            }
            return ids;
        } catch (SQLException failure) {
            throw unavailable();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Independent deletion ledger unavailable");
    }
}
