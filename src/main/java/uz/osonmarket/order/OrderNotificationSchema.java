package uz.osonmarket.order;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OrderNotificationSchema implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    OrderNotificationSchema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) return null;
            String values = Arrays.stream(OrderStatus.values()).map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));
            try (var statement = connection.createStatement()) {
                for (String table : List.of("order_notifications", "order_status_emails")) {
                    String constraint = table + "_status_check";
                    statement.execute("""
                            DO $migration$
                            BEGIN
                                IF EXISTS (
                                    SELECT 1 FROM pg_constraint
                                    WHERE conrelid = to_regclass('%1$s')
                                      AND conname = '%2$s'
                                      AND contype = 'c'
                                      AND strpos(pg_get_constraintdef(oid), 'READY_FOR_DELIVERY') = 0
                                ) THEN
                                    ALTER TABLE %1$s DROP CONSTRAINT IF EXISTS %2$s;
                                    ALTER TABLE %1$s ADD CONSTRAINT %2$s CHECK (status IN (%3$s));
                                END IF;
                            END;
                            $migration$;
                            """.formatted(table, constraint, values));
                }
            }
            return null;
        });
    }
}
