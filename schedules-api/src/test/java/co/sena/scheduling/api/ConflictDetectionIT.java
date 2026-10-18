package co.sena.scheduling.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-SCH-005 ITs contra Postgres real: los 6 tipos de conflicto y E1-E6.
 *
 * <p>Notas de cobertura:
 * <ul>
 *   <li>INSTRUCTOR_DOUBLE_BOOKED / ENVIRONMENT_DOUBLE_BOOKED: el EXCLUDE de BD
 *       impide que existan datos solapados con el mismo instructor/ambiente, así
 *       que en integración se cubre como última barrera (E6) y la lógica
 *       aplicativa de esos 2 tipos se cubre en ConflictDetectionServiceTest.</li>
 *   <li>Los otros 4 tipos no tienen restricción de BD: se verifican como filas
 *       persistidas en scheduling_conflict.</li>
 * </ul>
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
class ConflictDetectionIT {

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

    private static final String PERIOD_PREFIX = "2099-CF-";
    private static final String SLOT_PREFIX = "IT-CF-SLOT-";
    private static final UUID FICHA_ID =
        UUID.fromString("e1000000-0000-4000-8000-000000000001");
    private static final UUID TEST_USER_ID =
        UUID.fromString("e1000000-0000-4000-8000-000000000003");
    private static final UUID COMPETENCY_ID =
        UUID.fromString("e1000000-0000-4000-8000-000000000021");
    private static final UUID INSTRUCTOR_A =
        UUID.fromString("e1000000-0000-4000-8000-000000000011");
    private static final UUID INSTRUCTOR_B =
        UUID.fromString("e1000000-0000-4000-8000-000000000012");
    private static final UUID INSTRUCTOR_UNAVAILABLE =
        UUID.fromString("e1000000-0000-4000-8000-000000000013");
    private static final UUID ENVIRONMENT_A =
        UUID.fromString("e1000000-0000-4000-8000-000000000031");
    private static final UUID ENVIRONMENT_B =
        UUID.fromString("e1000000-0000-4000-8000-000000000032");
    private static final UUID ENVIRONMENT_UNDER_MAINTENANCE =
        UUID.fromString("e1000000-0000-4000-8000-000000000033");
    private static final UUID ENVIRONMENT_TRAVEL_ORIGIN =
        UUID.fromString("e1000000-0000-4000-8000-000000000034");
    private static final UUID ENVIRONMENT_TRAVEL_DESTINATION =
        UUID.fromString("e1000000-0000-4000-8000-000000000035");

    /** Martes 2099-03-03 (day_of_week = 2), festivo nacional improbabilísimo. */
    private static final String SESSION_DATE = "2099-03-03";
    private static final int SESSION_DAY = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
            "DELETE FROM scheduling.scheduling_conflict WHERE schedule_id IN " +
            "(SELECT id FROM scheduling.schedule WHERE period LIKE ?)", PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM scheduling.outbox WHERE event_id IN (SELECT id FROM scheduling.class_session " +
            "WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?))",
            PERIOD_PREFIX + "%");
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
        jdbcTemplate.update(
            "DELETE FROM scheduling.environment_availability_projection WHERE environment_id = ?",
            ENVIRONMENT_UNDER_MAINTENANCE);
        jdbcTemplate.update(
            "DELETE FROM scheduling.instructor_availability_projection WHERE instructor_id = ?",
            INSTRUCTOR_UNAVAILABLE);
        jdbcTemplate.update(
            "DELETE FROM scheduling.site_travel_time WHERE origin_site_id IN (?, ?) " +
            "AND destination_site_id IN (?, ?)",
            ENVIRONMENT_TRAVEL_ORIGIN, ENVIRONMENT_TRAVEL_DESTINATION,
            ENVIRONMENT_TRAVEL_ORIGIN, ENVIRONMENT_TRAVEL_DESTINATION);
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

    private UUID createDraft(String period) throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "ficha_id": "%s", "period": "%s", "name": "hu-005" }
                    """.formatted(FICHA_ID, period)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private UUID createSlot(String suffix, String start, String end) throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/time-slots")
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
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private UUID createSession(String period, UUID schedule, UUID slot, UUID instructor,
                               UUID environment) throws Exception {
        setAuth("SCH_EDIT");
        MvcResult result = mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
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
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private MvcResult validateSchedule(UUID scheduleId, int rowVersion) throws Exception {
        setAuth("SCH_EDIT");
        return mockMvc.perform(post("/api/v1/schedules/{id}/validate", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": " + rowVersion + " }"))
            .andReturn();
    }

    private MvcResult publishSchedule(UUID scheduleId, int rowVersion) throws Exception {
        setAuth("SCH_PUBLISH");
        return mockMvc.perform(post("/api/v1/schedules/{id}/publish", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": " + rowVersion + " }"))
            .andReturn();
    }

    private int countConflicts(UUID scheduleId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.scheduling_conflict WHERE schedule_id = ?",
            Integer.class, scheduleId);
        return count == null ? 0 : count;
    }

    private List<String> conflictTypes(UUID scheduleId) {
        return jdbcTemplate.queryForList(
            "SELECT conflict_type FROM scheduling.scheduling_conflict WHERE schedule_id = ?",
            String.class, scheduleId);
    }

    private void seedEnvironmentMaintenance() {
        jdbcTemplate.update("""
            INSERT INTO scheduling.environment_availability_projection
                (id, environment_id, available, effective_from, reason, last_event_id, updated_at)
            VALUES (?, ?, false, ?, 'mantenimiento IT HU-SCH-005', ?, now())
            """,
            UUID.randomUUID(), ENVIRONMENT_UNDER_MAINTENANCE,
            Timestamp.from(Instant.now().minusSeconds(86400L * 30)), UUID.randomUUID());
    }

    private void seedInstructorUnavailable() {
        jdbcTemplate.update("""
            INSERT INTO scheduling.instructor_availability_projection
                (id, instructor_id, competency_id, available, last_event_id, updated_at)
            VALUES (?, ?, null, false, ?, now())
            """,
            UUID.randomUUID(), INSTRUCTOR_UNAVAILABLE, UUID.randomUUID());
    }

    private void seedSiteTravelTime(int minutes) {
        jdbcTemplate.update("""
            INSERT INTO scheduling.site_travel_time
                (id, origin_site_id, destination_site_id, travel_minutes, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, true, now(), now())
            """,
            UUID.randomUUID(), ENVIRONMENT_TRAVEL_ORIGIN, ENVIRONMENT_TRAVEL_DESTINATION, minutes);
    }

    // ------------------------------------------------------------------ E1

    @Test
    void validateRequiresSchEdit() throws Exception {
        UUID scheduleId = createDraft(PERIOD_PREFIX + "1");
        // @PreAuthorize sin la feature: AccessDenied (en producción el filtro de
        // seguridad lo traduce a 403 FORBIDDEN; los filtros están desactivados en el IT).
        assertThrows(jakarta.servlet.ServletException.class,
            () -> mockMvc.perform(post("/api/v1/schedules/{id}/validate", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }")));
    }

    @Test
    void validateRejectsStaleRowVersion() throws Exception {
        UUID scheduleId = createDraft(PERIOD_PREFIX + "2");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/validate", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 99 }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ROW_VERSION_MISMATCH"));
    }

    @Test
    void validateWithoutRowVersionIsBadRequest() throws Exception {
        UUID scheduleId = createDraft(PERIOD_PREFIX + "3");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/validate", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void publishRequiresSchPublish() throws Exception {
        UUID scheduleId = createDraft(PERIOD_PREFIX + "4");
        assertThrows(jakarta.servlet.ServletException.class,
            () -> mockMvc.perform(post("/api/v1/schedules/{id}/publish", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }")));
    }

    @Test
    void publishRejectsStaleRowVersion() throws Exception {
        UUID scheduleId = createDraft(PERIOD_PREFIX + "5");
        assertStatus(validateSchedule(scheduleId, 0), "UNDER_REVIEW");
        setAuth("SCH_PUBLISH");
        mockMvc.perform(post("/api/v1/schedules/{id}/publish", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 42 }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ROW_VERSION_MISMATCH"));
    }

    // -------------------------------------------------------- E1.2 validate

    @Test
    void validateDetectsAndPersistsScheduleConflicts() throws Exception {
        String period = PERIOD_PREFIX + "6";
        UUID scheduleId = createDraft(period);
        UUID overlapSlot = createSlot("6A", "07:00", "10:00");
        UUID otherSlot = createSlot("6B", "08:00", "11:00");
        // Distintos instructores y ambientes: el EXCLUDE no aplica y la
        // detección aplicativa debe registrar SESSIONS_OVERLAP.
        createSession(period, scheduleId, overlapSlot, INSTRUCTOR_A, ENVIRONMENT_A);
        createSession(period, scheduleId, otherSlot, INSTRUCTOR_B, ENVIRONMENT_B);

        assertThat(conflictTypes(scheduleId)).containsExactly("SESSIONS_OVERLAP");

        assertStatus(validateSchedule(scheduleId, 0), "UNDER_REVIEW");

        // E1.2 + E3: la validación re-detecta sin duplicar.
        assertThat(conflictTypes(scheduleId)).containsExactly("SESSIONS_OVERLAP");
    }

    // -------------------------------------------------------- E1.3 publish

    @Test
    void publishIsBlockedByUnresolvedConflicts() throws Exception {
        String period = PERIOD_PREFIX + "7";
        UUID scheduleId = createDraft(period);
        UUID slotA = createSlot("7A", "07:00", "10:00");
        UUID slotB = createSlot("7B", "08:00", "11:00");
        createSession(period, scheduleId, slotA, INSTRUCTOR_A, ENVIRONMENT_A);
        createSession(period, scheduleId, slotB, INSTRUCTOR_B, ENVIRONMENT_B);
        assertStatus(validateSchedule(scheduleId, 0), "UNDER_REVIEW");

        setAuth("SCH_PUBLISH");
        mockMvc.perform(post("/api/v1/schedules/{id}/publish", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("UNRESOLVED_CONFLICTS"));

        assertThat(countConflicts(scheduleId)).isEqualTo(1);
    }

    @Test
    void publishSucceedsWhenScheduleHasNoConflicts() throws Exception {
        String period = PERIOD_PREFIX + "8";
        UUID scheduleId = createDraft(period);
        UUID slot = createSlot("8A", "07:00", "10:00");
        createSession(period, scheduleId, slot, INSTRUCTOR_A, ENVIRONMENT_A);
        assertThat(countConflicts(scheduleId)).isZero();

        assertStatus(validateSchedule(scheduleId, 0), "UNDER_REVIEW");
        assertStatus(publishSchedule(scheduleId, 0), "PUBLISHED");
    }

    // ------------------------------------------------------------- E3 (idem.)

    @Test
    void redetectionIsIdempotentAndDoesNotReopenResolvedConflicts() throws Exception {
        String period = PERIOD_PREFIX + "9";
        UUID scheduleId = createDraft(period);
        UUID slotA = createSlot("9A", "07:00", "10:00");
        UUID slotB = createSlot("9B", "08:00", "11:00");
        createSession(period, scheduleId, slotA, INSTRUCTOR_A, ENVIRONMENT_A);
        UUID secondSession = createSession(period, scheduleId, slotB, INSTRUCTOR_B, ENVIRONMENT_B);
        assertThat(countConflicts(scheduleId)).isEqualTo(1);

        // Re-detección vía PUT: misma clave, no se duplica.
        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/sessions/{id}", secondSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"notes\": \"re-deteccion PUT\" }"))
            .andExpect(status().isOk());
        assertThat(countConflicts(scheduleId)).isEqualTo(1);

        // Un humano resuelve el conflicto.
        jdbcTemplate.update(
            "UPDATE scheduling.scheduling_conflict SET is_resolved = true, is_blocking_publication = false, " +
            "resolved_by = ?, resolved_at = now(), resolution_reason = 'ajuste coordinado' " +
            "WHERE schedule_id = ?", TEST_USER_ID, scheduleId);

        // Re-detección vía PUT: no se reabre ni se duplica.
        mockMvc.perform(put("/api/v1/sessions/{id}", secondSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"notes\": \"post-resolucion\" }"))
            .andExpect(status().isOk());
        assertThat(countConflicts(scheduleId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT is_resolved FROM scheduling.scheduling_conflict WHERE schedule_id = ?",
            Boolean.class, scheduleId)).isTrue();

        // validate + publish completos: sigue sin duplicar y publica porque no
        // quedan conflictos activos (RN-SCH-04).
        assertStatus(validateSchedule(scheduleId, 0), "UNDER_REVIEW");
        assertThat(countConflicts(scheduleId)).isEqualTo(1);
        assertStatus(publishSchedule(scheduleId, 0), "PUBLISHED");
    }

    // ------------------------------------------------------------ E2: tipos

    @Test
    void recordsEnvironmentMaintenanceConflictOnSessionCreate() throws Exception {
        seedEnvironmentMaintenance();
        String period = PERIOD_PREFIX + "10";
        UUID scheduleId = createDraft(period);
        UUID slot = createSlot("10A", "07:00", "10:00");

        createSession(period, scheduleId, slot, INSTRUCTOR_A, ENVIRONMENT_UNDER_MAINTENANCE);

        setAuth("SCH_CONFLICT_VIEW");
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].conflict_type").value("ENVIRONMENT_MAINTENANCE"))
            .andExpect(jsonPath("$.data[0].session_b_id").doesNotExist())
            .andExpect(jsonPath("$.pagination.total_items").value(1));
    }

    @Test
    void recordsInstructorUnavailableConflictOnSessionCreate() throws Exception {
        seedInstructorUnavailable();
        String period = PERIOD_PREFIX + "11";
        UUID scheduleId = createDraft(period);
        UUID slot = createSlot("11A", "07:00", "10:00");

        createSession(period, scheduleId, slot, INSTRUCTOR_UNAVAILABLE, ENVIRONMENT_A);

        setAuth("SCH_CONFLICT_VIEW");
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].conflict_type").value("INSTRUCTOR_UNAVAILABLE"))
            .andExpect(jsonPath("$.data[0].session_b_id").doesNotExist());
    }

    @Test
    void recordsInstructorTravelConflictOnSessionCreate() throws Exception {
        seedSiteTravelTime(45);
        String period = PERIOD_PREFIX + "12";
        UUID scheduleId = createDraft(period);
        UUID morning = createSlot("12A", "07:00", "10:00");
        UUID next = createSlot("12B", "10:15", "12:15");

        createSession(period, scheduleId, morning, INSTRUCTOR_A, ENVIRONMENT_TRAVEL_ORIGIN);
        createSession(period, scheduleId, next, INSTRUCTOR_A, ENVIRONMENT_TRAVEL_DESTINATION);

        setAuth("SCH_CONFLICT_VIEW");
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].conflict_type").value("INSTRUCTOR_TRAVEL_CONFLICT"));
    }

    @Test
    void noTravelConflictWhenGapCoversTravelTime() throws Exception {
        seedSiteTravelTime(45);
        String period = PERIOD_PREFIX + "13";
        UUID scheduleId = createDraft(period);
        UUID morning = createSlot("13A", "07:00", "10:00");
        UUID next = createSlot("13B", "10:45", "12:45");

        createSession(period, scheduleId, morning, INSTRUCTOR_A, ENVIRONMENT_TRAVEL_ORIGIN);
        createSession(period, scheduleId, next, INSTRUCTOR_A, ENVIRONMENT_TRAVEL_DESTINATION);

        assertThat(countConflicts(scheduleId)).isZero();
    }

    @Test
    void recordsEnvironmentDoubleBookingAsSessionCreateConflict() throws Exception {
        String period = PERIOD_PREFIX + "14";
        UUID scheduleId = createDraft(period);
        UUID slotA = createSlot("14A", "07:00", "10:00");
        UUID slotB = createSlot("14B", "08:00", "11:00");

        createSession(period, scheduleId, slotA, INSTRUCTOR_A, ENVIRONMENT_A);
        setAuth("SCH_EDIT");
        // Mismo ambiente solapado: el EXCLUDE (E6) lo rechaza en BD.
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "competency_id": "%s",
                      "environment_id": "%s",
                      "instructor_id": "%s",
                      "time_slot_id": "%s",
                      "session_date": "%s"
                    }
                    """.formatted(COMPETENCY_ID, ENVIRONMENT_A, INSTRUCTOR_B, slotB, SESSION_DATE)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ENVIRONMENT_DOUBLE_BOOKED"));
    }

    @Test
    void recordsInstructorDoubleBookingAsSessionCreateConflict() throws Exception {
        String period = PERIOD_PREFIX + "15";
        UUID scheduleId = createDraft(period);
        UUID slotA = createSlot("15A", "07:00", "10:00");
        UUID slotB = createSlot("15B", "08:00", "11:00");

        createSession(period, scheduleId, slotA, INSTRUCTOR_A, ENVIRONMENT_A);
        setAuth("SCH_EDIT");
        // Mismo instructor solapado: el EXCLUDE (E6) lo rechaza en BD.
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", scheduleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "competency_id": "%s",
                      "environment_id": "%s",
                      "instructor_id": "%s",
                      "time_slot_id": "%s",
                      "session_date": "%s"
                    }
                    """.formatted(COMPETENCY_ID, ENVIRONMENT_B, INSTRUCTOR_A, slotB, SESSION_DATE)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INSTRUCTOR_DOUBLE_BOOKED"));
    }

    // --------------------------------------------------- E1: PUT de sesión

    @Test
    void detectsConflictOnSessionUpdate() throws Exception {
        String period = PERIOD_PREFIX + "16";
        UUID scheduleId = createDraft(period);
        UUID morning = createSlot("16A", "07:00", "10:00");
        UUID overlapping = createSlot("16B", "08:00", "11:00");
        createSession(period, scheduleId, morning, INSTRUCTOR_A, ENVIRONMENT_A);
        UUID secondSession = createSession(period, scheduleId, overlapping, INSTRUCTOR_B, ENVIRONMENT_B);
        assertThat(countConflicts(scheduleId)).isEqualTo(1);

        // PUT: mueve la segunda sesión a una franja que ya no solapa.
        UUID late = createSlot("16C", "12:00", "15:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/sessions/{id}", secondSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"time_slot_id\": \"" + late + "\" }"))
            .andExpect(status().isOk());

        // E3: el conflicto dejó de existir → se retira (sin filas huérfanas).
        assertThat(countConflicts(scheduleId)).isZero();
    }

    // ------------------------------------------------------------- E5

    @Test
    void listsConflictsWithFiltersAndPagination() throws Exception {
        seedEnvironmentMaintenance();
        String period = PERIOD_PREFIX + "17";
        UUID scheduleId = createDraft(period);
        UUID slotA = createSlot("17A", "07:00", "10:00");
        UUID slotB = createSlot("17B", "08:00", "11:00");
        createSession(period, scheduleId, slotA, INSTRUCTOR_A, ENVIRONMENT_UNDER_MAINTENANCE);
        createSession(period, scheduleId, slotB, INSTRUCTOR_B, ENVIRONMENT_B);
        // 2 conflictos: ENVIRONMENT_MAINTENANCE (simple) + SESSIONS_OVERLAP (par).
        assertThat(countConflicts(scheduleId)).isEqualTo(2);

        setAuth("SCH_CONFLICT_VIEW");

        // Sin filtros: ambos con paginación real.
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("page", "1").param("page_size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.pagination.total_items").value(2))
            .andExpect(jsonPath("$.pagination.total_pages").value(2));

        // Filtro por tipo.
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("conflict_type", "SESSIONS_OVERLAP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].conflict_type").value("SESSIONS_OVERLAP"))
            .andExpect(jsonPath("$.pagination.total_items").value(1));

        // Filtro is_resolved=false: los 2 siguen activos.
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("is_resolved", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(2));

        // Resuelvo uno y el filtro lo saca del conjunto activo.
        jdbcTemplate.update(
            "UPDATE scheduling.scheduling_conflict SET is_resolved = true, is_blocking_publication = false " +
            "WHERE schedule_id = ? AND conflict_type = 'SESSIONS_OVERLAP'", scheduleId);
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("is_resolved", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(1))
            .andExpect(jsonPath("$.data[*].conflict_type", hasItem("ENVIRONMENT_MAINTENANCE")))
            .andExpect(jsonPath("$.data[*].conflict_type", not(hasItem("SESSIONS_OVERLAP"))));

        // Filtro fuera del enum del contrato → 400 (mismo patrón que ?status= en GET /schedules).
        mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)
                .param("conflict_type", "NO_EXISTE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsConflictListingWithoutSchConflictView() throws Exception {
        String period = PERIOD_PREFIX + "18";
        UUID scheduleId = createDraft(period);
        assertThrows(jakarta.servlet.ServletException.class,
            () -> mockMvc.perform(get("/api/v1/schedules/{id}/conflicts", scheduleId)));
    }

    /** Valida 200 + el campo `status` del ScheduleResponse del contrato. */
    private void assertStatus(MvcResult result, String expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus())
            .as("respuesta: %s", result.getResponse().getContentAsString())
            .isEqualTo(200);
        JsonNode node;
        try {
            node = objectMapper.readTree(result.getResponse().getContentAsString());
        } catch (Exception ex) {
            throw new AssertionError("No se pudo leer la respuesta: " + ex.getMessage(), ex);
        }
        assertThat(node.path("status").asText()).isEqualTo(expectedStatus);
    }
}
