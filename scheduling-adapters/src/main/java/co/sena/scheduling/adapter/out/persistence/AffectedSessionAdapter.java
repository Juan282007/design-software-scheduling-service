package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.application.port.out.AffectedSessionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class AffectedSessionAdapter implements AffectedSessionPort {

    private final JdbcTemplate jdbc;

    public AffectedSessionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> findActiveSessionIdsByEnvironmentIdAndDateRange(UUID environmentId, LocalDate startDate, LocalDate endDate) {
        return jdbc.queryForList("""
            SELECT id FROM scheduling.class_session
            WHERE environment_id = ? AND session_date BETWEEN ? AND ? AND status = 'ACTIVE' AND is_active = true
            """, UUID.class, environmentId, Date.valueOf(startDate), Date.valueOf(endDate));
    }
}
