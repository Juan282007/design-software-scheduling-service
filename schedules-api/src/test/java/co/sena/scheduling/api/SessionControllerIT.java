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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ITs HU-SCH-003 contra Postgres real (no mocks), incl. EXCLUDE con datos reales.
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
class SessionControllerIT {

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

    private static final String PERIOD_PREFIX = "2099-S3-";
    private static final String SLOT_PREFIX = "IT-S3-SLOT-";
    private static final UUID FICHA_ID =
        UUID.fromString("d1000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_FICHA_ID =
        UUID.fromString("d1000000-0000-4000-8000-000000000004");
    private static final UUID INSTRUCTOR_A =
        UUID.fromString("d1000000-0000-4000-8000-000000000011");
    private static final UUID INSTRUCTOR_B =
        UUID.fromString("d1000000-0000-4000-8000-000000000012");
    private static final UUID COMPETENCY_ID =
        UUID.fromString("d1000000-0000-4000-8000-000000000021");
    private static final UUID ENVIRONMENT_A =
        UUID.fromString("d1000000-0000-4000-8000-000000000031");
    private static final UUID ENVIRONMENT_B =
        UUID.fromString("d1000000-0000-4000-8000-000000000032");
    private static final UUID TEST_USER_ID =
        UUID.fromString("d1000000-0000-4000-8000-000000000003");
    private static final UUID TRAINING_CENTER_ID =
        UUID.fromString("d1000000-0000-4000-8000-000000000005");
    private static final UUID OTHER_TRAINING_CENTER_ID =
        UUID.fromString("d1000000-0000-4000-8000-000000000006");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
            "DELETE FROM scheduling.outbox WHERE event_id IN (SELECT id FROM scheduling.class_session WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?))",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM scheduling.class_session WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?)",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "UPDATE scheduling.schedule SET status = 'ARCHIVED' WHERE status = 'PUBLISHED' AND period LIKE ?",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE period LIKE ?",
            PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM scheduling.ficha_training_center_projection WHERE ficha_id IN (?, ?)",
            FICHA_ID, OTHER_FICHA_ID);
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

    private void setJwtAuth(List<String> features, UUID actorId, String... authorities) {
        setJwtAuth(features, actorId, null, authorities);
    }

    private void setJwtAuth(List<String> features, UUID actorId, UUID fichaId, String... authorities) {
        setJwtAuth(features, actorId, fichaId, null, authorities);
    }

    private void setJwtAuth(List<String> features, UUID actorId, UUID fichaId,
                            UUID trainingCenterId, String... authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", TEST_USER_ID.toString());
        claims.put("features", features);
        if (actorId != null) {
            claims.put("actor_id", actorId.toString());
        }
        if (fichaId != null) {
            claims.put("ficha_id", fichaId.toString());
        }
        if (trainingCenterId != null) {
            claims.put("training_center_id", trainingCenterId.toString());
        }
        Jwt jwt = new Jwt("fake-token", Instant.now(), Instant.now().plusSeconds(900),
            Map.of("alg", "RS256"), claims);
        List<SimpleGrantedAuthority> auths = Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, auths));
    }

    @Test
    void createsSessionCopyingSlot() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "1");
        UUID slot = createSlot(SLOT_PREFIX + "1", 2, "07:00", "10:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "competency_id": "d1000000-0000-4000-8000-000000000021",
                      "environment_id": "d1000000-0000-4000-8000-000000000031",
                      "instructor_id": "d1000000-0000-4000-8000-000000000011",
                      "time_slot_id": "%s",
                      "session_date": "2099-02-03",
                      "notes": "primera"
                    }
                    """.formatted(slot)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.day_of_week").value(2))
            .andExpect(jsonPath("$.start_time").value("07:00"))
            .andExpect(jsonPath("$.end_time").value("10:00"));
    }

    @Test
    void rejectsCreateUnderPublishedSchedule() throws Exception {
        UUID schedule = insertSchedule(PERIOD_PREFIX + "2", "PUBLISHED");
        UUID slot = createSlot(SLOT_PREFIX + "2", 3, "07:00", "10:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(slot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-03-03")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SCHEDULE_IMMUTABLE"));
    }

    @Test
    void rejectsInstructorDoubleBooking() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "3");
        UUID slotA = createSlot(SLOT_PREFIX + "3A", 2, "07:00", "10:00");
        UUID slotB = createSlot(SLOT_PREFIX + "3B", 2, "09:00", "12:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(slotA, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03")))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(slotB, INSTRUCTOR_A, ENVIRONMENT_B, "2099-02-03")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INSTRUCTOR_DOUBLE_BOOKED"));
    }

    @Test
    void rejectsEnvironmentDoubleBooking() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "4");
        UUID slotA = createSlot(SLOT_PREFIX + "4A", 4, "07:00", "10:00");
        UUID slotB = createSlot(SLOT_PREFIX + "4B", 4, "09:00", "12:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(slotA, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-05")))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(slotB, INSTRUCTOR_B, ENVIRONMENT_A, "2099-02-05")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ENVIRONMENT_DOUBLE_BOOKED"));
    }

    @Test
    void rejectsShortDurationSundayAndHoliday() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "5");
        UUID shortSlot = createSlot(SLOT_PREFIX + "5S", 2, "07:00", "07:30");
        UUID sundaySlot = createSlot(SLOT_PREFIX + "5D", 7, "07:00", "10:00");
        UUID normalSlot = createSlot(SLOT_PREFIX + "5N", 2, "07:00", "10:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(shortSlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(sundaySlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(normalSlot, INSTRUCTOR_A, ENVIRONMENT_A, "2026-01-01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMovableHolidayAndDayMismatch() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "5B");
        // Viernes Santo 2026-04-03 (viernes) con franja de viernes: festivo movible.
        UUID fridaySlot = createSlot(SLOT_PREFIX + "5F", 5, "07:00", "10:00");
        // Franja martes con fecha miércoles: incoherencia día/fecha.
        UUID tuesdaySlot = createSlot(SLOT_PREFIX + "5T", 2, "07:00", "10:00");
        setAuth("SCH_EDIT");
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(fridaySlot, INSTRUCTOR_A, ENVIRONMENT_A, "2026-04-03")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(tuesdaySlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-04")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updatesCancelsAndDeletes() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "6");
        UUID slot = createSlot(SLOT_PREFIX + "6", 2, "07:00", "10:00");
        UUID id = createSession(schedule, slot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03");

        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/sessions/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "notes": "ajustada", "instructor_id": "d1000000-0000-4000-8000-000000000012" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value("ajustada"))
            .andExpect(jsonPath("$.instructor_id").value(INSTRUCTOR_B.toString()));

        mockMvc.perform(post("/api/v1/sessions/{id}/cancel", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        Integer staged = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.outbox WHERE event_id = ? AND event_type = 'scheduling.class_session.cancelled' AND status = 'PENDING'",
            Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(staged).isEqualTo(1);
        mockMvc.perform(post("/api/v1/sessions/{id}/cancel", id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SESSION_ALREADY_CANCELLED"));

        mockMvc.perform(delete("/api/v1/sessions/{id}", id))
            .andExpect(status().isNoContent());
        Integer inactive = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.class_session WHERE id = ? AND is_active = false",
            Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(inactive).isEqualTo(1);

        setAuth("SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/sessions/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void listsWithInstructorScope() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "7");
        UUID draftSchedule = createSchedule(PERIOD_PREFIX + "7D");
        UUID slot = createSlot(SLOT_PREFIX + "7", 2, "07:00", "10:00");
        UUID ownId = createSession(schedule, slot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03");
        UUID otherSlot = createSlot(SLOT_PREFIX + "7B", 3, "07:00", "10:00");
        UUID otherId = createSession(schedule, otherSlot, INSTRUCTOR_B, ENVIRONMENT_B, "2099-02-04");
        UUID draftSlot = createSlot(SLOT_PREFIX + "7D", 4, "07:00", "10:00");
        UUID draftId = createSession(draftSchedule, draftSlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-05");
        publishForScope(schedule);

        // OWN_SCHEDULE fuerza instructor del JWT e ignora el query param (HU-008 E2).
        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_SCHEDULE"), INSTRUCTOR_A, "SCH_VIEW_OWN");
        MvcResult result = mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10")
                .param("instructor_id", INSTRUCTOR_B.toString()))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains(ownId.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(otherId.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(draftId.toString());

        mockMvc.perform(get("/api/v1/sessions/{id}", otherId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sessions/{id}", ownId))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/{id}", draftId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/schedules/{id}/sessions", draftSchedule)
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(0));
    }

    @Test
    void listsWithLearnerFichaScope() throws Exception {
        UUID ownSchedule = createSchedule(PERIOD_PREFIX + "7L", FICHA_ID);
        UUID otherSchedule = createSchedule(PERIOD_PREFIX + "7M", OTHER_FICHA_ID);
        UUID draftSchedule = createSchedule(PERIOD_PREFIX + "7D", FICHA_ID);
        UUID ownSlot = createSlot(SLOT_PREFIX + "7L", 2, "07:00", "10:00");
        UUID otherSlot = createSlot(SLOT_PREFIX + "7M", 3, "07:00", "10:00");
        UUID draftSlot = createSlot(SLOT_PREFIX + "7LD", 4, "07:00", "10:00");
        UUID ownId = createSession(ownSchedule, ownSlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03");
        UUID otherId = createSession(otherSchedule, otherSlot, INSTRUCTOR_B, ENVIRONMENT_B, "2099-02-04");
        UUID draftId = createSession(draftSchedule, draftSlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-05");
        publishForScope(ownSchedule);
        publishForScope(otherSchedule);

        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_FICHA_AS_LEARNER"), null, FICHA_ID, "SCH_VIEW_OWN");
        MvcResult result = mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains(ownId.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(otherId.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(draftId.toString());

        mockMvc.perform(get("/api/v1/schedules/{id}/sessions", draftSchedule)
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(0));

        mockMvc.perform(get("/api/v1/schedules/{id}/sessions", otherSchedule)
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(0));

        mockMvc.perform(get("/api/v1/sessions/{id}", otherId))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sessions/{id}", ownId))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/{id}", draftId))
            .andExpect(status().isNotFound());
    }

    @Test
    void listsWithTrainingCenterScopeForViewAll() throws Exception {
        UUID schedule = createSchedule(PERIOD_PREFIX + "7T");
        UUID slot = createSlot(SLOT_PREFIX + "7T", 2, "07:00", "10:00");
        UUID session = createSession(schedule, slot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03");
        mapFichaToTrainingCenter(FICHA_ID, TRAINING_CENTER_ID);

        setJwtAuth(List.of("SCH_VIEW_ALL:TRAINING_CENTER"), null, null, TRAINING_CENTER_ID, "SCH_VIEW_ALL");
        MvcResult result = mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
            .contains(session.toString());

        mockMvc.perform(get("/api/v1/sessions/{id}", session))
            .andExpect(status().isOk());
    }

    @Test
    void trainingCenterScopeFiltersSessionsByScheduleFichaCenter() throws Exception {
        UUID ownSchedule = createSchedule(PERIOD_PREFIX + "7A", FICHA_ID);
        UUID otherSchedule = createSchedule(PERIOD_PREFIX + "7B", OTHER_FICHA_ID);
        UUID ownSlot = createSlot(SLOT_PREFIX + "7TA", 2, "07:00", "10:00");
        UUID otherSlot = createSlot(SLOT_PREFIX + "7TB", 3, "07:00", "10:00");
        UUID ownSession = createSession(ownSchedule, ownSlot, INSTRUCTOR_A, ENVIRONMENT_A, "2099-02-03");
        UUID otherSession = createSession(otherSchedule, otherSlot, INSTRUCTOR_B, ENVIRONMENT_B, "2099-02-04");
        mapFichaToTrainingCenter(FICHA_ID, TRAINING_CENTER_ID);
        mapFichaToTrainingCenter(OTHER_FICHA_ID, OTHER_TRAINING_CENTER_ID);

        setJwtAuth(List.of("SCH_VIEW_ALL:TRAINING_CENTER"), null, null, TRAINING_CENTER_ID, "SCH_VIEW_ALL");
        MvcResult result = mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains(ownSession.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(otherSession.toString());

        mockMvc.perform(get("/api/v1/schedules/{id}/sessions", otherSchedule)
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(0));

        mockMvc.perform(get("/api/v1/sessions/{id}", otherSession))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sessions/{id}", ownSession))
            .andExpect(status().isOk());
    }

    @Test
    void trainingCenterScopeWithoutClaimIsFailClosed() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_ALL:TRAINING_CENTER"), null, "SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void learnerScopeWithoutFichaClaimIsFailClosed() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_FICHA_AS_LEARNER"), null, "SCH_VIEW_OWN");
        mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void unresolvableScopeIsFailClosed() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_OWN:TRAINING_CENTER"), INSTRUCTOR_A, "SCH_VIEW_OWN");
        mockMvc.perform(get("/api/v1/sessions")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String sessionBody(UUID slot, UUID instructor, UUID environment, String date) {
        return """
            {
              "competency_id": "d1000000-0000-4000-8000-000000000021",
              "environment_id": "%s",
              "instructor_id": "%s",
              "time_slot_id": "%s",
              "session_date": "%s"
            }
            """.formatted(environment, instructor, slot, date);
    }

    private UUID createSchedule(String period) throws Exception {
        return createSchedule(period, FICHA_ID);
    }

    private UUID createSchedule(String period, UUID fichaId) throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "ficha_id": "%s", "period": "%s", "name": "s3" }
                    """.formatted(fichaId, period)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private UUID insertSchedule(String period, String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, name, status, row_version,
                     created_by, created_at, updated_at, is_active)
                VALUES (?, ?, ?, 's3-fixture', ?, 0, ?, ?, ?, true)
                """,
            id, FICHA_ID, period, status, TEST_USER_ID, now, now);
        return id;
    }

    private UUID createSlot(String name, int dayOfWeek, String start, String end) throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "day_of_week": %d,
                      "start_time": "%s",
                      "end_time": "%s",
                      "shift": "DAY"
                    }
                    """.formatted(name, dayOfWeek, start, end)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private UUID createSession(UUID schedule, UUID slot, UUID instructor, UUID environment,
                               String date) throws Exception {
        setAuth("SCH_EDIT");
        MvcResult result = mockMvc.perform(post("/api/v1/schedules/{id}/sessions", schedule)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionBody(slot, instructor, environment, date)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private void publishForScope(UUID scheduleId) {
        jdbcTemplate.update("UPDATE scheduling.schedule SET status = 'PUBLISHED' WHERE id = ?", scheduleId);
    }

    private void mapFichaToTrainingCenter(UUID fichaId, UUID trainingCenterId) {
        jdbcTemplate.update("""
                INSERT INTO scheduling.ficha_training_center_projection
                    (ficha_id, training_center_id, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (ficha_id) DO UPDATE
                SET training_center_id = EXCLUDED.training_center_id,
                    updated_at = EXCLUDED.updated_at
                """,
            fichaId, trainingCenterId, Timestamp.from(Instant.now()));
    }
}
