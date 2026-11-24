package uz.osonmarket.reporting.admin;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/statistics")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatisticsController {
    private final AdminStatisticsService statistics;

    public AdminStatisticsController(AdminStatisticsService statistics) {
        this.statistics = statistics;
    }

    @GetMapping
    public Map<String, Object> overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statistics.report(Section.OVERVIEW, from, to, 20);
    }

    @GetMapping("/{section}")
    public Map<String, Object> report(@PathVariable Section section,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return statistics.report(section, from, to, limit);
    }

    public enum Section { OVERVIEW, ORDERS, CATALOG, CUSTOMERS, BRANCHES, ENGAGEMENT, OPERATIONS }
}
