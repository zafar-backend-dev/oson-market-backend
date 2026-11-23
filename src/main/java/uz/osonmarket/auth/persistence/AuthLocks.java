package uz.osonmarket.auth.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthLocks {
    private final JdbcTemplate jdbc;

    public AuthLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(String identifier) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                statement -> statement.setString(1, identifier),
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {});
    }
}
