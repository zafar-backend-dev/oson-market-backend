package uz.osonmarket.reporting.admin;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uz.osonmarket.auth.api.AuthException;
import uz.osonmarket.reporting.admin.AdminStatisticsController.Section;

@Service
class AdminStatisticsService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private final NamedParameterJdbcTemplate jdbc;

    AdminStatisticsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> report(Section section, LocalDate from, LocalDate to, int limit) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= 366
                || start.getYear() < 1 || end.getYear() > 9999) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "statistics.invalidRange");
        }
        var parameters = Map.of("start", Timestamp.from(start.atStartOfDay(ZONE).toInstant()),
                "end", Timestamp.from(end.plusDays(1).atStartOfDay(ZONE).toInstant()),
                "fromDate", start, "toDate", end, "zone", ZONE.getId(), "limit", limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("section", section);
        result.put("from", start);
        result.put("to", end);
        result.put("timezone", ZONE.getId());
        for (var query : StatisticsQueries.forSection(section)) {
            result.put(query.name(), query.single()
                    ? jdbc.queryForMap(query.sql(), parameters)
                    : jdbc.queryForList(query.sql(), parameters));
        }
        return result;
    }
}
