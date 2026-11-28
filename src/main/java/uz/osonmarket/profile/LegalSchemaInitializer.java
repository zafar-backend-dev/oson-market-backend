package uz.osonmarket.profile;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.persistence.AuthLocks;

@Component
@org.springframework.core.annotation.Order(0)
public class LegalSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate db;
    private final AuthLocks locks;

    public LegalSchemaInitializer(JdbcTemplate db, AuthLocks locks) {
        this.db = db;
        this.locks = locks;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        locks.lock("legal-schema-initializer");
        db.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/manual/20261201_legal_publication.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/manual/20261202_account_erasure.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/manual/20261203_retention.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/manual/20261204_backup_admin.sql"));
            return null;
        });
    }
}
