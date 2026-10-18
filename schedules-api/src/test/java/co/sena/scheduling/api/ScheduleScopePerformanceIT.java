package co.sena.scheduling.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NFR-PERF-04: consulta de horario por instructor/ficha debe responder p95 < 500 ms.
 *
 * Esta prueba es una verificacion de performance reproducible contra Postgres real
 * dentro de la suite de integracion. La prueba de carga formal sigue perteneciendo
 * al gate de performance/gobernanza del ambiente.
 */
@SpringBootTest(classes = SchedulesApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.default_schema=scheduling",
    "spring.task.scheduling.enabled=false",
    "outbox.enabled=false"
})
class ScheduleScopePerformanceIT {

    private static final long P95_THRESHOLD_MS = 500;
    private static final int WARMUP_RUNS = 10;
    private static final int MEASURED_RUNS = 60;
    private static final String PERIOD_PREFIX = "2099-P";
    private static final String SLOT_PREFIX = "IT-PERF-SLOT-";
    private static final UUID TEST_USER_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000003");
    private static final UUID FICHA_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_FICHA_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000002");
    private static final UUID INSTRUCTOR_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000011");
    private static final UUID OTHER_INSTRUCTOR_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000012");
    private static final UUID ENVIRONMENT_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000021");

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedData() {
        SecurityContextHolder.clearContext();
        cleanUp();
        Timestamp now = Timestamp.from(Instant.now());
        UUID slot = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scheduling.time_slot
                    (id, name, day_of_week, start_time, end_time, shift, is_active)
                VALUES (?, ?, 2, '07:00:00', '10:00:00', 'DAY', true)
                """, slot, SLOT_PREFIX + "1");

        insertScheduleWithSession(PERIOD_PREFIX + "1", FICHA_ID, INSTRUCTOR_ID, slot,
            "2099-02-03", now);
        insertScheduleWithSession(PERIOD_PREFIX + "2", OTHER_FICHA_ID, OTHER_INSTRUCTOR_ID, slot,
            "2099-02-04", now);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
            "DELETE FROM scheduling.class_session WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?)",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "UPDATE scheduling.schedule SET status = 'ARCHIVED' WHERE status = 'PUBLISHED' AND period LIKE ?",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE period LIKE ?",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.time_slot WHERE name LIKE ?",
            SLOT_PREFIX + "%");
    }

    @Test
    void instructorWeekQueryMeetsP95Under500ms() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_SCHEDULE"), INSTRUCTOR_ID, null, "SCH_VIEW_OWN");
        for (int i = 0; i < WARMUP_RUNS; i++) {
            instructorWeekQuery();
        }

        long p95 = measureP95Millis(() -> instructorWeekQuery());

        assertThat(p95)
            .as("NFR-PERF-04 instructor/week p95")
            .isLessThan(P95_THRESHOLD_MS);
    }

    @Test
    void learnerFichaQueryMeetsP95Under500ms() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_FICHA_AS_LEARNER"), null, FICHA_ID, "SCH_VIEW_OWN");
        for (int i = 0; i < WARMUP_RUNS; i++) {
            learnerFichaQuery();
        }

        long p95 = measureP95Millis(() -> learnerFichaQuery());

        assertThat(p95)
            .as("NFR-PERF-04 learner/ficha p95")
            .isLessThan(P95_THRESHOLD_MS);
    }

    private void instructorWeekQuery() throws Exception {
        mockMvc.perform(get("/api/v1/sessions")
                .param("instructor_id", OTHER_INSTRUCTOR_ID.toString())
                .param("from", "2099-02-01")
                .param("to", "2099-02-08")
                .param("page", "1")
                .param("page_size", "20"))
            .andExpect(status().isOk());
    }

    private void learnerFichaQuery() throws Exception {
        mockMvc.perform(get("/api/v1/schedules")
                .param("ficha_id", OTHER_FICHA_ID.toString())
                .param("page", "1")
                .param("page_size", "20"))
            .andExpect(status().isOk());
    }

    private long measureP95Millis(CheckedRunnable operation) throws Exception {
        List<Long> durations = new ArrayList<>(MEASURED_RUNS);
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            operation.run();
            durations.add((System.nanoTime() - start) / 1_000_000);
        }
        durations.sort(Long::compareTo);
        int index = (int) Math.ceil(durations.size() * 0.95) - 1;
        return durations.get(Math.max(0, index));
    }

    private void insertScheduleWithSession(String period, UUID fichaId, UUID instructorId,
                                           UUID slotId, String date, Timestamp now) {
        UUID scheduleId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, name, status, row_version,
                     created_by, created_at, updated_at, is_active)
                VALUES (?, ?, ?, 'perf-fixture', 'DRAFT', 0, ?, ?, ?, true)
                """,
            scheduleId, fichaId, period, TEST_USER_ID, now, now);
        jdbcTemplate.update("""
                INSERT INTO scheduling.class_session
                    (id, schedule_id, competency_id, environment_id, instructor_id,
                     time_slot_id, start_time, end_time, day_of_week, session_date,
                     status, updated_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, '07:00:00', '10:00:00', 2, ?,
                        'ACTIVE', ?, true)
                """,
            UUID.randomUUID(), scheduleId, UUID.randomUUID(), ENVIRONMENT_ID,
            instructorId, slotId, java.sql.Date.valueOf(date), now);
    }

    private void setJwtAuth(List<String> features, UUID actorId, UUID fichaId, String... authorities) {
        java.util.HashMap<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", TEST_USER_ID.toString());
        claims.put("features", features);
        if (actorId != null) {
            claims.put("actor_id", actorId.toString());
        }
        if (fichaId != null) {
            claims.put("ficha_id", fichaId.toString());
        }
        Jwt jwt = new Jwt("fake-token", Instant.now(), Instant.now().plusSeconds(900),
            Map.of("alg", "RS256"), claims);
        List<SimpleGrantedAuthority> auths = java.util.Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, auths));
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
