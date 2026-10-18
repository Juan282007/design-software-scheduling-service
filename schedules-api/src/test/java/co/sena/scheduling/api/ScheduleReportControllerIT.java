package co.sena.scheduling.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SchedulesApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.default_schema=scheduling",
    "spring.task.scheduling.enabled=false",
    "outbox.enabled=false"
})
class ScheduleReportControllerIT {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            valueOrDefault("SCHEDULING_DB_URL", "jdbc:postgresql://localhost:5432/design-software-develop"));
        registry.add("spring.datasource.username", () ->
            valueOrDefault("SCHEDULING_DB_USERNAME", "design_software_user"));
        registry.add("spring.datasource.password", () ->
            valueOrDefault("SCHEDULING_DB_PASSWORD", ""));
    }

    private static String valueOrDefault(String name, String defaultValue) {
        String systemValue = System.getProperty(name);
        if (systemValue != null) {
            return systemValue;
        }
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static final String PERIOD_PREFIX = "2099-RPT-";
    private static final String SLOT_PREFIX = "RPT-SLOT-";
    private static final UUID USER_ID = UUID.fromString("e2000000-0000-4000-8000-000000000001");
    private static final UUID FICHA_A = UUID.fromString("e2000000-0000-4000-8000-000000000011");
    private static final UUID FICHA_B = UUID.fromString("e2000000-0000-4000-8000-000000000012");
    private static final UUID INSTRUCTOR_A = UUID.fromString("e2000000-0000-4000-8000-000000000021");
    private static final UUID INSTRUCTOR_B = UUID.fromString("e2000000-0000-4000-8000-000000000022");
    private static final UUID ENV_A = UUID.fromString("e2000000-0000-4000-8000-000000000031");
    private static final UUID ENV_B = UUID.fromString("e2000000-0000-4000-8000-000000000032");
    private static final UUID COMPETENCY = UUID.fromString("e2000000-0000-4000-8000-000000000041");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID scheduleA;
    private UUID scheduleB;
    private UUID sessionA1;
    private UUID sessionA2;
    private UUID sessionB1;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        seedFixture();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM scheduling.scheduling_conflict WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?)", PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.class_session WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?)", PERIOD_PREFIX + "%");
        jdbcTemplate.update("UPDATE scheduling.schedule SET status = 'ARCHIVED' WHERE status = 'PUBLISHED' AND period LIKE ?", PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE period LIKE ?", PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.time_slot WHERE name LIKE ?", SLOT_PREFIX + "%");
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportsScheduleByInstructor() throws Exception {
        setReportAuth();
        mockMvc.perform(get("/api/v1/reports/schedule-by-instructor")
                .param("instructor_id", INSTRUCTOR_A.toString())
                .param("from", "2099-04-01")
                .param("to", "2099-04-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].instructor_id").value(INSTRUCTOR_A.toString()))
            .andExpect(jsonPath("$.data[0].schedule_count").value(2))
            .andExpect(jsonPath("$.data[0].session_count").value(2))
            .andExpect(jsonPath("$.data[0].total_hours").value(6.0));
    }

    @Test
    void reportsScheduleByFicha() throws Exception {
        setReportAuth();
        mockMvc.perform(get("/api/v1/reports/schedule-by-ficha")
                .param("ficha_id", FICHA_A.toString())
                .param("period", PERIOD_PREFIX + "A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].ficha_id").value(FICHA_A.toString()))
            .andExpect(jsonPath("$.data[0].schedule_id").value(scheduleA.toString()))
            .andExpect(jsonPath("$.data[0].session_count").value(2))
            .andExpect(jsonPath("$.data[0].instructor_count").value(2))
            .andExpect(jsonPath("$.data[0].environment_count").value(2));
    }

    @Test
    void reportsScheduleByEnvironmentAndOccupancy() throws Exception {
        setReportAuth();
        mockMvc.perform(get("/api/v1/reports/schedule-by-environment")
                .param("environment_id", ENV_A.toString())
                .param("from", "2099-04-01")
                .param("to", "2099-04-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].environment_id").value(ENV_A.toString()))
            .andExpect(jsonPath("$.data[0].session_count").value(2))
            .andExpect(jsonPath("$.data[0].ficha_count").value(2));

        mockMvc.perform(get("/api/v1/reports/environment-occupancy")
                .param("environment_id", ENV_A.toString())
                .param("from", "2099-04-01")
                .param("to", "2099-04-08"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].environment_id").value(ENV_A.toString()))
            .andExpect(jsonPath("$.data[0].occupied_slots").value(2))
            .andExpect(jsonPath("$.data[0].total_slots").isNumber())
            .andExpect(jsonPath("$.data[0].occupancy_rate").isNumber());
    }

    @Test
    void reportsScheduleConflictsWithCursor() throws Exception {
        setReportAuth();
        var first = mockMvc.perform(get("/api/v1/reports/schedule-conflicts")
                .param("schedule_id", scheduleA.toString())
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].schedule_id").value(scheduleA.toString()))
            .andExpect(jsonPath("$.pagination.has_more").value(true))
            .andReturn();

        String body = first.getResponse().getContentAsString();
        assertThat(body).contains("next_cursor");
        String nextCursor = objectMapper.readTree(body).get("pagination").get("next_cursor").asText();

        mockMvc.perform(get("/api/v1/reports/schedule-conflicts")
                .param("schedule_id", scheduleA.toString())
                .param("cursor", nextCursor)
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].schedule_id").value(scheduleA.toString()))
            .andExpect(jsonPath("$.pagination.has_more").value(false));
    }

    @Test
    void requiresReportFeature() {
        setAuth("SCH_VIEW_ALL");

        assertThatThrownBy(() -> mockMvc.perform(get("/api/v1/reports/schedule-by-instructor")))
            .hasRootCauseInstanceOf(org.springframework.security.authorization.AuthorizationDeniedException.class);
    }

    private void setReportAuth() {
        setAuth("SCH_REPORT_VIEW");
    }

    private void setAuth(String... features) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(USER_ID.toString(), null,
                List.of(features).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    private void seedFixture() {
        UUID slotA = insertSlot(SLOT_PREFIX + "A", 2, "07:00:00", "10:00:00");
        UUID slotB = insertSlot(SLOT_PREFIX + "B", 3, "10:00:00", "13:00:00");
        scheduleA = insertSchedule(FICHA_A, PERIOD_PREFIX + "A", "PUBLISHED");
        scheduleB = insertSchedule(FICHA_B, PERIOD_PREFIX + "B", "PUBLISHED");
        sessionA1 = insertSession(scheduleA, slotA, INSTRUCTOR_A, ENV_A, "2099-04-02", 2, "07:00:00", "10:00:00");
        sessionA2 = insertSession(scheduleA, slotB, INSTRUCTOR_B, ENV_B, "2099-04-03", 3, "10:00:00", "13:00:00");
        sessionB1 = insertSession(scheduleB, slotA, INSTRUCTOR_A, ENV_A, "2099-04-04", 4, "07:00:00", "10:00:00");
        insertConflict(scheduleA, sessionA1, sessionA2, "INSTRUCTOR_DOUBLE_BOOKED", Instant.parse("2099-04-02T12:00:00Z"));
        insertConflict(scheduleA, sessionA2, null, "SESSIONS_OVERLAP", Instant.parse("2099-04-02T13:00:00Z"));
    }

    private UUID insertSchedule(UUID fichaId, String period, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2099-04-01T00:00:00Z"));
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, name, status, row_version,
                     created_by, created_at, updated_at, is_active)
                VALUES (?, ?, ?, 'report-fixture', ?, 0, ?, ?, ?, true)
                """, id, fichaId, period, status, USER_ID, now, now);
        return id;
    }

    private UUID insertSlot(String name, int day, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scheduling.time_slot
                    (id, name, day_of_week, start_time, end_time, shift, is_active)
                VALUES (?, ?, ?, ?, ?, 'DAY', true)
                """, id, name, day, Time.valueOf(start), Time.valueOf(end));
        return id;
    }

    private UUID insertSession(UUID scheduleId, UUID slotId, UUID instructorId, UUID environmentId,
                               String date, int day, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scheduling.class_session
                    (id, schedule_id, competency_id, environment_id, instructor_id, time_slot_id,
                     start_time, end_time, day_of_week, session_date, status, updated_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, 'ACTIVE', ?, true)
                """, id, scheduleId, COMPETENCY, environmentId, instructorId, slotId,
            Time.valueOf(start), Time.valueOf(end), day, date, Timestamp.from(Instant.now()));
        return id;
    }

    private void insertConflict(UUID scheduleId, UUID sessionA, UUID sessionB, String type, Instant detectedAt) {
        jdbcTemplate.update("""
                INSERT INTO scheduling.scheduling_conflict
                    (id, schedule_id, session_a_id, session_b_id, conflict_type, description,
                     is_resolved, detected_at, severity, is_blocking_publication)
                VALUES (?, ?, ?, ?, ?, 'fixture conflict', false, ?, 'HIGH', true)
                """, UUID.randomUUID(), scheduleId, sessionA, sessionB, type, Timestamp.from(detectedAt));
    }
}
