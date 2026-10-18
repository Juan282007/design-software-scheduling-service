package co.sena.scheduling.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-SCH-006 ITs contra Postgres real: resolución de conflictos (E1-E3).
 *
 * <p>Cubre el "Cómo probar" del Work Item:
 * resolve 200 + columnas G1, 409 repetido, 403 sin feature, 404 desconocido,
 * publish deja de bloquear, re-detección no reabre, listado de auditoría.
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
class ConflictResolveIT {

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

    private static final String PERIOD_PREFIX = "2099-RS-";
    private static final String SLOT_PREFIX = "IT-RS-SLOT-";
    private static final UUID FICHA_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000001");
    private static final UUID TEST_USER_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000003");
    private static final UUID COMPETENCY_ID =
        UUID.fromString("f1000000-0000-4000-8000-000000000021");
    private static final UUID INSTRUCTOR_A =
        UUID.fromString("f1000000-0000-4000-8000-000000000011");
    private static final UUID INSTRUCTOR_B =
        UUID.fromString("f1000000-0000-4000-8000-000000000012");
    private static final UUID ENVIRONMENT_A =
        UUID.fromString("f1000000-0000-4000-8000-000000000031");
    private static final UUID ENVIRONMENT_B =
        UUID.fromString("f1000000-0000-4000-8000-000000000032");

    /** Martes 2099-03-10 (day_of_week = 2). */
    private static final String SESSION_DATE = "2099-03-10";
    private static final int SESSION_DAY = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
            "DELETE FROM scheduling.scheduling_conflict WHERE schedule_id IN " +
            "(SELECT id FROM scheduling.schedule WHERE period LIKE ?)", PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM scheduling.class_session WHERE schedule_id IN " +
            "(SELECT id FROM scheduling.schedule WHERE period LIKE ?)", PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "UPDATE scheduling.schedule SET status = 'ARCHIVED' WHERE status IN ('PUBLISHED','UNDER_REVIEW') " +
            "AND period LIKE ?", PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE period LIKE ?",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.time_slot WHERE name LIKE ?",
            SLOT_PREFIX + "%");
    }

    @BeforeEach
    void setupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth(String... features) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(features)
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(TEST_USER_ID.toString(), null, authorities));
    }

    // --------------------------------------------------------------- helpers

    private UUID createDraft() throws Exception {
        // period: máx 10 caracteres (validación HU-002); el cleanup libera el valor tras cada test.
        return createDraft(PERIOD_PREFIX + "1");
    }

    private UUID createDraft(String period) throws Exception {
        setAuth("SCH_CREATE");
        var result = mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "ficha_id": "%s", "period": "%s", "name": "hu-006" }
                    """.formatted(FICHA_ID, period)))
            .andExpect(status().isCreated())
            .andReturn();
        return readId(result);
    }

    private UUID readId(org.springframework.test.web.servlet.MvcResult result) {
        try {
            return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private UUID createSlot(String suffix, String start, String end) throws Exception {
        setAuth("SCH_CREATE");
        var result = mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s%s",
                      "day_of_week": %d,
                      "start_time": "%s",
                      "end_time": "%s",
                      "shift": "DAY"
                    }
                    """.formatted(SLOT_PREFIX, suffix, SESSION_DAY, start, end)))
            .andExpect(status().isCreated())
            .andReturn();
        return readId(result);
    }

    private UUID createSession(UUID schedule, UUID slot, UUID instructor, UUID environment)
            throws Exception {
        setAuth("SCH_EDIT");
        var result = mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "competency_id": "%s",
                      "environment_id": "%s",
                      "instructor_id": "%s",
                      "time_slot_id": "%s",
                      "session_date": "%s"
                    }
                    """.formatted(COMPETENCY_ID, environment, instructor, slot, SESSION_DATE)))
            .andExpect(status().isCreated())
            .andReturn();
        return readId(result);
    }

    /** Crea un borrador con dos sesiones solapadas → 1 conflicto SESSIONS_OVERLAP. */
    private UUID scheduleWithOneConflict() throws Exception {
        UUID scheduleId = createDraft();
        UUID slotA = createSlot("A", "07:00", "10:00");
        UUID slotB = createSlot("B", "08:00", "11:00");
        createSession(scheduleId, slotA, INSTRUCTOR_A, ENVIRONMENT_A);
        createSession(scheduleId, slotB, INSTRUCTOR_B, ENVIRONMENT_B);
        assertThat(countConflicts(scheduleId)).isEqualTo(1);
        return scheduleId;
    }

    private UUID conflictId(UUID scheduleId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM scheduling.scheduling_conflict WHERE schedule_id = ?",
            UUID.class, scheduleId);
    }

    private int countConflicts(UUID scheduleId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.scheduling_conflict WHERE schedule_id = ?",
            Integer.class, scheduleId);
        return count == null ? 0 : count;
    }

    private void validateOk(UUID scheduleId) throws Exception {
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/validate", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }"))
            .andExpect(status().isOk());
    }

    private void resolve(UUID conflictId) throws Exception {
        setAuth("SCH_CONFLICT_RESOLVE");
        mockMvc.perform(post("/api/v1/conflicts/{id}/resolve", conflictId))
            .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ tests

    @Test
    void resolveMarksConflictAsResolvedWithActorAndTimestamp() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        UUID conflictId = conflictId(scheduleId);

        setAuth("SCH_CONFLICT_RESOLVE");
        mockMvc.perform(post("/api/v1/conflicts/{id}/resolve", conflictId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(conflictId.toString()))
            .andExpect(jsonPath("$.is_resolved").value(true))
            .andExpect(jsonPath("$.conflict_type").value("SESSIONS_OVERLAP"))
            // E4: columnas G1 no se exponen en el contrato.
            .andExpect(jsonPath("$.resolved_by").doesNotExist())
            .andExpect(jsonPath("$.resolved_at").doesNotExist());

        // E2: columnas G1 persistidas en BD.
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT is_resolved, resolved_by, resolved_at, resolution_reason, " +
            "is_blocking_publication FROM scheduling.scheduling_conflict WHERE id = ?",
            conflictId);
        assertThat(row.get("is_resolved")).isEqualTo(true);
        assertThat(row.get("resolved_by")).isEqualTo(TEST_USER_ID);
        assertThat(row.get("resolved_at")).isNotNull();
        assertThat(row.get("resolution_reason")).isNull();
        assertThat(row.get("is_blocking_publication")).isEqualTo(false);
    }

    @Test
    void resolveReturns409WhenAlreadyResolved() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        UUID conflictId = conflictId(scheduleId);
        resolve(conflictId);

        setAuth("SCH_CONFLICT_RESOLVE");
        mockMvc.perform(post("/api/v1/conflicts/{id}/resolve", conflictId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT_ALREADY_RESOLVED"));
    }

    @Test
    void resolveRequiresSchConflictResolve() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        UUID conflictId = conflictId(scheduleId);
        // Autenticado pero sin la feature: AccessDenied (403 en producción con filtros).
        setAuth("SCH_VIEW_ALL");
        assertThrows(jakarta.servlet.ServletException.class,
            () -> mockMvc.perform(post("/api/v1/conflicts/{id}/resolve", conflictId)));
    }

    @Test
    void resolveReturns404ForUnknownConflict() throws Exception {
        setAuth("SCH_CONFLICT_RESOLVE");
        mockMvc.perform(post("/api/v1/conflicts/{id}/resolve", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("CONFLICT_NOT_FOUND"));
    }

    @Test
    void publishSucceedsAfterConflictIsResolved() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        UUID conflictId = conflictId(scheduleId);
        validateOk(scheduleId);

        // E3: con el conflicto sin resolver el publish daría 409.
        resolve(conflictId);

        setAuth("SCH_PUBLISH");
        mockMvc.perform(post("/api/v1/schedules/{id}/publish", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // E3: el registro permanece (auditoría histórica).
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT is_resolved, status FROM scheduling.scheduling_conflict c " +
            "JOIN scheduling.schedule s ON s.id = c.schedule_id WHERE c.id = ?", conflictId);
        assertThat(row.get("is_resolved")).isEqualTo(true);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    void redetectionDoesNotReopenResolvedConflict() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        UUID conflictId = conflictId(scheduleId);
        resolve(conflictId);

        // Re-detección vía PUT de sesión (HU-SCH-005): no reabre ni duplica.
        UUID secondSession = jdbcTemplate.queryForObject(
            "SELECT id FROM scheduling.class_session WHERE schedule_id = ? " +
            "AND start_time = '08:00:00'", UUID.class, scheduleId);
        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/sessions/{id}", secondSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"notes\": \"post-resolucion\" }"))
            .andExpect(status().isOk());

        assertThat(countConflicts(scheduleId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT is_resolved FROM scheduling.scheduling_conflict WHERE id = ?",
            Boolean.class, conflictId)).isTrue();
    }

    @Test
    void resolvedConflictRemainsListedForAudit() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        resolve(conflictId(scheduleId));

        setAuth("SCH_CONFLICT_VIEW");
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("is_resolved", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(1))
            .andExpect(jsonPath("$.data[0].is_resolved").value(true));

        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("is_resolved", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(0));
    }

    /**
     * PR #12 / QA blocker: carrera de resolución concurrente. Dos peticiones
     * disparadas a la vez contra el mismo conflicto deben producir exactamente
     * un 200 (ganador) y un 409 (perdedor: "already resolved by another user").
     * Lo garantiza la actualización condicional atómica
     * {@code UPDATE ... WHERE is_resolved = false}.
     */
    @Test
    void concurrentResolveProducesExactlyOneWinner() throws Exception {
        UUID scheduleId = scheduleWithOneConflict();
        UUID conflictId = conflictId(scheduleId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> resolveRequest = () -> {
            setAuth("SCH_CONFLICT_RESOLVE");
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/v1/conflicts/{id}/resolve", conflictId))
                .andReturn().getResponse().getStatus();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        int first;
        int second;
        try {
            Future<Integer> firstFuture = pool.submit(resolveRequest);
            Future<Integer> secondFuture = pool.submit(resolveRequest);
            first = firstFuture.get(15, TimeUnit.SECONDS);
            second = secondFuture.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(List.of(first, second)).containsExactlyInAnyOrder(200, 409);

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT is_resolved, resolved_by, resolved_at FROM scheduling.scheduling_conflict WHERE id = ?",
            conflictId);
        assertThat(row.get("is_resolved")).isEqualTo(true);
        assertThat(row.get("resolved_by")).isEqualTo(TEST_USER_ID);
        assertThat(row.get("resolved_at")).isNotNull();
    }
}
