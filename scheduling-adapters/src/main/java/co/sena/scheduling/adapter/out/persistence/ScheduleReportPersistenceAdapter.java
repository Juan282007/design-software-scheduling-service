package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.application.port.in.ScheduleReportUseCase;
import co.sena.scheduling.application.port.out.ScheduleReportPersistencePort;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component
public class ScheduleReportPersistenceAdapter implements ScheduleReportPersistencePort {

    private final JdbcTemplate jdbcTemplate;

    public ScheduleReportPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.ScheduleByInstructor> scheduleByInstructor(
            UUID instructorId, LocalDate from, LocalDate to, int page, int pageSize, String sort) {
        SqlParts parts = new SqlParts();
        parts.where("cs.is_active = true");
        parts.where("cs.deleted_at IS NULL");
        parts.where("s.is_active = true");
        parts.where("s.deleted_at IS NULL");
        if (instructorId != null) {
            parts.where("cs.instructor_id = ?", instructorId);
        }
        if (from != null) {
            parts.where("cs.session_date >= ?", java.sql.Date.valueOf(from));
        }
        if (to != null) {
            parts.where("cs.session_date < ?", java.sql.Date.valueOf(to));
        }
        String fromSql = " FROM scheduling.class_session cs JOIN scheduling.schedule s ON s.id = cs.schedule_id ";
        long total = countGroups("SELECT COUNT(*) FROM (SELECT cs.instructor_id" + fromSql
                + parts.whereClause() + " GROUP BY cs.instructor_id) q", parts.params());
        String order = switch (sort == null ? "" : sort.trim()) {
            case "session_count:desc" -> " session_count DESC, instructor_id ASC ";
            case "session_count:asc" -> " session_count ASC, instructor_id ASC ";
            case "total_hours:desc" -> " total_hours DESC, instructor_id ASC ";
            case "total_hours:asc" -> " total_hours ASC, instructor_id ASC ";
            default -> " instructor_id ASC ";
        };
        List<Object> params = new ArrayList<>(parts.params());
        params.add(pageSize);
        params.add(offset(page, pageSize));
        List<ScheduleReportUseCase.ScheduleByInstructor> data = jdbcTemplate.query("""
                SELECT cs.instructor_id,
                       COUNT(DISTINCT cs.schedule_id) AS schedule_count,
                       COUNT(*) AS session_count,
                       ROUND(COALESCE(SUM(EXTRACT(EPOCH FROM (cs.end_time - cs.start_time)) / 3600.0), 0)::numeric, 2) AS total_hours
                """ + fromSql + parts.whereClause() + " GROUP BY cs.instructor_id ORDER BY " + order
                + " LIMIT ? OFFSET ?", this::mapInstructor, params.toArray());
        return new ScheduleReportUseCase.OffsetPage<>(data, page, pageSize, total);
    }

    @Override
    public ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.ScheduleByFicha> scheduleByFicha(
            UUID fichaId, String period, String status, Instant from, Instant to, int page, int pageSize, String sort) {
        SqlParts parts = new SqlParts();
        parts.where("s.is_active = true");
        parts.where("s.deleted_at IS NULL");
        if (fichaId != null) {
            parts.where("s.ficha_id = ?", fichaId);
        }
        if (period != null && !period.isBlank()) {
            parts.where("s.period = ?", period);
        }
        if (status != null && !status.isBlank()) {
            parts.where("s.status = ?", status);
        }
        if (from != null) {
            parts.where("s.created_at >= ?", Timestamp.from(from));
        }
        if (to != null) {
            parts.where("s.created_at < ?", Timestamp.from(to));
        }
        String fromSql = " FROM scheduling.schedule s LEFT JOIN scheduling.class_session cs ON cs.schedule_id = s.id AND cs.is_active = true AND cs.deleted_at IS NULL ";
        long total = countGroups("SELECT COUNT(*) FROM (SELECT s.id" + fromSql
                + parts.whereClause() + " GROUP BY s.id) q", parts.params());
        String order = switch (sort == null ? "" : sort.trim()) {
            case "period:desc" -> " s.period DESC, s.id ASC ";
            case "period:asc" -> " s.period ASC, s.id ASC ";
            case "session_count:desc" -> " session_count DESC, s.id ASC ";
            case "session_count:asc" -> " session_count ASC, s.id ASC ";
            default -> " s.created_at ASC, s.id ASC ";
        };
        List<Object> params = new ArrayList<>(parts.params());
        params.add(pageSize);
        params.add(offset(page, pageSize));
        List<ScheduleReportUseCase.ScheduleByFicha> data = jdbcTemplate.query("""
                SELECT s.ficha_id,
                       s.id AS schedule_id,
                       s.period,
                       s.status,
                       COUNT(cs.id) AS session_count,
                       COUNT(DISTINCT cs.instructor_id) AS instructor_count,
                       COUNT(DISTINCT cs.environment_id) AS environment_count
                """ + fromSql + parts.whereClause()
                + " GROUP BY s.id, s.ficha_id, s.period, s.status, s.created_at ORDER BY " + order
                + " LIMIT ? OFFSET ?", this::mapFicha, params.toArray());
        return new ScheduleReportUseCase.OffsetPage<>(data, page, pageSize, total);
    }

    @Override
    public ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.ScheduleByEnvironment> scheduleByEnvironment(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort) {
        SqlParts parts = sessionReportWhere(environmentId, from, to);
        String fromSql = " FROM scheduling.class_session cs JOIN scheduling.schedule s ON s.id = cs.schedule_id ";
        long total = countGroups("SELECT COUNT(*) FROM (SELECT cs.environment_id" + fromSql
                + parts.whereClause() + " GROUP BY cs.environment_id) q", parts.params());
        String order = switch (sort == null ? "" : sort.trim()) {
            case "session_count:desc" -> " session_count DESC, environment_id ASC ";
            case "session_count:asc" -> " session_count ASC, environment_id ASC ";
            case "total_hours:desc" -> " total_hours DESC, environment_id ASC ";
            case "total_hours:asc" -> " total_hours ASC, environment_id ASC ";
            default -> " environment_id ASC ";
        };
        List<Object> params = new ArrayList<>(parts.params());
        params.add(pageSize);
        params.add(offset(page, pageSize));
        List<ScheduleReportUseCase.ScheduleByEnvironment> data = jdbcTemplate.query("""
                SELECT cs.environment_id,
                       COUNT(*) AS session_count,
                       ROUND(COALESCE(SUM(EXTRACT(EPOCH FROM (cs.end_time - cs.start_time)) / 3600.0), 0)::numeric, 2) AS total_hours,
                       COUNT(DISTINCT s.ficha_id) AS ficha_count
                """ + fromSql + parts.whereClause() + " GROUP BY cs.environment_id ORDER BY " + order
                + " LIMIT ? OFFSET ?", this::mapEnvironment, params.toArray());
        return new ScheduleReportUseCase.OffsetPage<>(data, page, pageSize, total);
    }

    @Override
    public ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.EnvironmentOccupancy> environmentOccupancy(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort) {
        SqlParts parts = sessionReportWhere(environmentId, from, to);
        String fromSql = " FROM scheduling.class_session cs JOIN scheduling.schedule s ON s.id = cs.schedule_id ";
        long total = countGroups("SELECT COUNT(*) FROM (SELECT cs.environment_id" + fromSql
                + parts.whereClause() + " GROUP BY cs.environment_id) q", parts.params());
        LocalDate periodFrom = from == null ? LocalDate.now() : from;
        LocalDate periodTo = to == null ? periodFrom.plusDays(7) : to;
        long dayCount = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(periodFrom, periodTo));
        Long activeSlots = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM scheduling.time_slot
                WHERE is_active = true AND deleted_at IS NULL
                """, Long.class);
        long totalSlotsBase = Math.max(1, activeSlots == null ? 0 : activeSlots) * dayCount;
        String order = switch (sort == null ? "" : sort.trim()) {
            case "occupancy_rate:desc" -> " occupancy_rate DESC, environment_id ASC ";
            case "occupancy_rate:asc" -> " occupancy_rate ASC, environment_id ASC ";
            default -> " environment_id ASC ";
        };
        List<Object> params = new ArrayList<>();
        params.add(periodFrom);
        params.add(periodTo);
        params.add(totalSlotsBase);
        params.add(totalSlotsBase);
        params.addAll(parts.params());
        params.add(pageSize);
        params.add(offset(page, pageSize));
        List<ScheduleReportUseCase.EnvironmentOccupancy> data = jdbcTemplate.query("""
                SELECT cs.environment_id,
                       ?::date AS period_from,
                       ?::date AS period_to,
                       COUNT(*) AS occupied_slots,
                       ?::bigint AS total_slots,
                       ROUND((COUNT(*)::numeric / NULLIF(?::bigint, 0)), 4) AS occupancy_rate
                """ + fromSql + parts.whereClause()
                + " GROUP BY cs.environment_id ORDER BY " + order + " LIMIT ? OFFSET ?",
                this::mapOccupancy, params.toArray());
        return new ScheduleReportUseCase.OffsetPage<>(data, page, pageSize, total);
    }

    @Override
    public ScheduleReportUseCase.CursorPage<ScheduleReportUseCase.ScheduleConflictReport> scheduleConflicts(
            String cursor, int limit, UUID scheduleId, String conflictType, Boolean resolved, Instant from, Instant to) {
        SqlParts parts = new SqlParts();
        if (scheduleId != null) {
            parts.where("schedule_id = ?", scheduleId);
        }
        if (conflictType != null && !conflictType.isBlank()) {
            parts.where("conflict_type = ?", conflictType);
        }
        if (resolved != null) {
            parts.where("is_resolved = ?", resolved);
        }
        if (from != null) {
            parts.where("detected_at >= ?", Timestamp.from(from));
        }
        if (to != null) {
            parts.where("detected_at < ?", Timestamp.from(to));
        }
        Cursor decoded = decodeCursor(cursor);
        if (decoded != null) {
            parts.where("(detected_at, id) > (?, ?)", Timestamp.from(decoded.detectedAt()), decoded.id());
        }
        List<Object> params = new ArrayList<>(parts.params());
        params.add(limit + 1);
        List<ScheduleReportUseCase.ScheduleConflictReport> rows = jdbcTemplate.query("""
                SELECT id, schedule_id, session_a_id, session_b_id, conflict_type, description,
                       is_resolved, detected_at
                FROM scheduling.scheduling_conflict
                """ + parts.whereClause() + " ORDER BY detected_at ASC, id ASC LIMIT ?",
                this::mapConflict, params.toArray());
        boolean hasMore = rows.size() > limit;
        List<ScheduleReportUseCase.ScheduleConflictReport> page = hasMore ? rows.subList(0, limit) : rows;
        String next = null;
        if (hasMore && !page.isEmpty()) {
            ScheduleReportUseCase.ScheduleConflictReport last = page.get(page.size() - 1);
            next = encodeCursor(last.detectedAt(), last.id());
        }
        return new ScheduleReportUseCase.CursorPage<>(List.copyOf(page), next, limit, hasMore);
    }

    private SqlParts sessionReportWhere(UUID environmentId, LocalDate from, LocalDate to) {
        SqlParts parts = new SqlParts();
        parts.where("cs.is_active = true");
        parts.where("cs.deleted_at IS NULL");
        parts.where("cs.status = 'ACTIVE'");
        parts.where("s.is_active = true");
        parts.where("s.deleted_at IS NULL");
        if (environmentId != null) {
            parts.where("cs.environment_id = ?", environmentId);
        }
        if (from != null) {
            parts.where("cs.session_date >= ?", java.sql.Date.valueOf(from));
        }
        if (to != null) {
            parts.where("cs.session_date < ?", java.sql.Date.valueOf(to));
        }
        return parts;
    }

    private long countGroups(String sql, List<Object> params) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    private int offset(int page, int pageSize) {
        return (page - 1) * pageSize;
    }


    private ScheduleReportUseCase.ScheduleByInstructor mapInstructor(ResultSet rs, int row) throws SQLException {
        return new ScheduleReportUseCase.ScheduleByInstructor(
                rs.getObject("instructor_id", UUID.class),
                rs.getLong("schedule_count"),
                rs.getLong("session_count"),
                decimal(rs.getBigDecimal("total_hours")));
    }

    private ScheduleReportUseCase.ScheduleByFicha mapFicha(ResultSet rs, int row) throws SQLException {
        return new ScheduleReportUseCase.ScheduleByFicha(
                rs.getObject("ficha_id", UUID.class),
                rs.getObject("schedule_id", UUID.class),
                rs.getString("period"),
                rs.getString("status"),
                rs.getLong("session_count"),
                rs.getLong("instructor_count"),
                rs.getLong("environment_count"));
    }

    private ScheduleReportUseCase.ScheduleByEnvironment mapEnvironment(ResultSet rs, int row) throws SQLException {
        return new ScheduleReportUseCase.ScheduleByEnvironment(
                rs.getObject("environment_id", UUID.class),
                rs.getLong("session_count"),
                decimal(rs.getBigDecimal("total_hours")),
                rs.getLong("ficha_count"));
    }

    private ScheduleReportUseCase.EnvironmentOccupancy mapOccupancy(ResultSet rs, int row) throws SQLException {
        return new ScheduleReportUseCase.EnvironmentOccupancy(
                rs.getObject("environment_id", UUID.class),
                rs.getObject("period_from", LocalDate.class),
                rs.getObject("period_to", LocalDate.class),
                rs.getLong("occupied_slots"),
                rs.getLong("total_slots"),
                decimal(rs.getBigDecimal("occupancy_rate")));
    }

    private ScheduleReportUseCase.ScheduleConflictReport mapConflict(ResultSet rs, int row) throws SQLException {
        return new ScheduleReportUseCase.ScheduleConflictReport(
                rs.getObject("id", UUID.class),
                rs.getObject("schedule_id", UUID.class),
                rs.getObject("session_a_id", UUID.class),
                rs.getObject("session_b_id", UUID.class),
                rs.getString("conflict_type"),
                rs.getString("description"),
                rs.getBoolean("is_resolved"),
                rs.getTimestamp("detected_at").toInstant());
    }

    private BigDecimal decimal(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String encodeCursor(Instant detectedAt, UUID id) {
        String raw = detectedAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("bad cursor");
            }
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new ScheduleValidationException("cursor", "INVALID_VALUE", "cursor invalido");
        }
    }

    private record Cursor(Instant detectedAt, UUID id) {}

    private static class SqlParts {
        private final List<String> predicates = new ArrayList<>();
        private final List<Object> params = new ArrayList<>();

        void where(String predicate, Object... values) {
            predicates.add(predicate);
            params.addAll(List.of(values));
        }

        String whereClause() {
            return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        }

        List<Object> params() {
            return params;
        }
    }
}
