package uz.osonmarket.auth.config;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.persistence.AccountProfiles;
import uz.osonmarket.auth.persistence.AuthLocks;
import uz.osonmarket.auth.persistence.UserRepository;

@Component
@Order(0)
public class AuthSeed implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final AuthLocks locks;
    private final UserRepository users;
    private final AccountProfiles profiles;
    private final String username;
    private final String email;
    private final String hash;

    public AuthSeed(JdbcTemplate jdbc, AuthLocks locks, UserRepository users, AccountProfiles profiles,
                    @Value("${app.default-admin.username:}") String username,
                    @Value("${app.default-admin.email:}") String email,
                    @Value("${app.default-admin.password-hash:}") String hash) {
        this.jdbc = jdbc;
        this.locks = locks;
        this.users = users;
        this.profiles = profiles;
        this.username = username;
        this.email = email;
        this.hash = hash;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.update("insert into languages (code, enabled, is_default) values "
                + "('uz', true, true), ('ru', true, false) on conflict do nothing");
        if (username.isBlank() || email.isBlank() || !hash.matches("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
            LoggerFactory.getLogger(AuthSeed.class).info("Default admin requires a valid BCrypt hash in application.yaml");
            return;
        }
        locks.lock("account:" + email);
        locks.lock("username:" + username);
        if (!users.existsByEmail(email) && !users.existsByUsername(username)) {
            profiles.create(email, username, hash, null, null, null, "uz", "ADMIN");
        }
    }
}
