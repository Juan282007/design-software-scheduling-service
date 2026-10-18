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

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
 * ITs HU-SCH-002 contra Postgres real (no mocks), estilo TimeSlotControllerIT.
 * Requiere SCHEDULING_DB_URL/USERNAME/PASSWORD o defaults de develop.
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
class ScheduleControllerIT {

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

    private static final String TEST_PERIOD_PREFIX = "2099-IT-";
    private static final UUID TEST_FICHA_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000001");
    private static final UUID PUBLISHED_FICHA_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_FICHA_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000004");
    private static final UUID TEST_USER_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000003");
    private static final UUID TRAINING_CENTER_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000005");
    private static final UUID OTHER_TRAINING_CENTER_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000006");
    private static final UUID SPOOFED_PUBLISHER_ID =
        UUID.fromString("c1000000-0000-4000-8000-000000000099");
    private static final UUID INSTRUCTOR_A =
        UUID.fromString("c1000000-0000-4000-8000-000000000011");
    private static final UUID INSTRUCTOR_B =
        UUID.fromString("c1000000-0000-4000-8000-000000000012");
    private static final String SLOT_PREFIX = "IT-SLOT-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // El trigger trg_schedule_published_immutability bloquea el DELETE físico
        // de PUBLISHED: primero se archivan (única transición permitida) y luego se borran.
        // Orden FK: sesiones -> horarios -> franjas.
        jdbcTemplate.update("""
            DELETE FROM scheduling.outbox
            WHERE event_type = 'scheduling.schedule.published'
              AND payload->>'schedule_id' IN (
                SELECT id::text FROM scheduling.schedule WHERE period LIKE ?
              )
            """, TEST_PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM scheduling.class_session WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period LIKE ?)",
            TEST_PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "UPDATE scheduling.schedule SET status = 'ARCHIVED' WHERE status = 'PUBLISHED' AND period LIKE ?",
            TEST_PERIOD_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE period LIKE ?",
            TEST_PERIOD_PREFIX + "%");
        jdbcTemplate.update(
            "DELETE FROM scheduling.ficha_training_center_projection WHERE ficha_id IN (?, ?, ?)",
            TEST_FICHA_ID, PUBLISHED_FICHA_ID, OTHER_FICHA_ID);
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

    /** Auth JWT como en producción (SecurityConfig): features CODE:SCOPE + actor_id. */
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
    void createsDraftWithVersionZero() throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ficha_id": "c1000000-0000-4000-8000-000000000001",
                      "period": "2099-IT-1",
                      "name": "IT borrador"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.row_version").value(0))
            .andExpect(jsonPath("$.ficha_id").value(TEST_FICHA_ID.toString()))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.schedule WHERE id = ? AND is_active = true",
            Integer.class, UUID.fromString(body.get("id").asText()));
        org.assertj.core.api.Assertions.assertThat(active).isEqualTo(1);
    }

    @Test
    void allowsReplacementDraftWhenPublishedExists() throws Exception {
        insertPublished(TEST_PERIOD_PREFIX + "2", PUBLISHED_FICHA_ID);
        setAuth("SCH_CREATE");
        mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ficha_id": "c1000000-0000-4000-8000-000000000002",
                      "period": "2099-IT-2",
                      "name": "duplicado"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void rejectsCreateWithMissingFields() throws Exception {
        setAuth("SCH_CREATE");
        mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "period": "2099-IT-3" }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listsWithFiltersAndPagination() throws Exception {
        createDraft(TEST_PERIOD_PREFIX + "4", TEST_FICHA_ID);
        setAuth("SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10")
                .param("ficha_id", TEST_FICHA_ID.toString())
                .param("period", TEST_PERIOD_PREFIX + "4")
                .param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].period").value(TEST_PERIOD_PREFIX + "4"))
            .andExpect(jsonPath("$.pagination.page").value(1))
            .andExpect(jsonPath("$.pagination.total_items").value(1));
    }

    @Test
    void getsAndUpdatesDraft() throws Exception {
        UUID id = createDraft(TEST_PERIOD_PREFIX + "5", TEST_FICHA_ID);
        setAuth("SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/schedules/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));

        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/schedules/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "renombrado", "row_version": 0 }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("renombrado"))
            .andExpect(jsonPath("$.row_version").value(1));
    }

    @Test
    void rejectsUpdateWithStaleRowVersion() throws Exception {
        UUID id = createDraft(TEST_PERIOD_PREFIX + "6", TEST_FICHA_ID);
        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/schedules/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "x", "row_version": 99 }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ROW_VERSION_MISMATCH"));
    }

    @Test
    void rejectsUpdateOfPublishedAsImmutable() throws Exception {
        UUID id = insertPublished(TEST_PERIOD_PREFIX + "7", PUBLISHED_FICHA_ID);
        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/schedules/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "x", "row_version": 0 }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SCHEDULE_IMMUTABLE"));
    }

    @Test
    void deletesDraftWithSoftDelete() throws Exception {
        UUID id = createDraft(TEST_PERIOD_PREFIX + "8", TEST_FICHA_ID);
        setAuth("SCH_DELETE_DRAFT");
        mockMvc.perform(delete("/api/v1/schedules/{id}", id).param("row_version", "0"))
            .andExpect(status().isNoContent());

        Integer inactive = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.schedule WHERE id = ? AND is_active = false",
            Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(inactive).isEqualTo(1);

        setAuth("SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/schedules/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeleteOfPublishedAsImmutable() throws Exception {
        UUID id = insertPublished(TEST_PERIOD_PREFIX + "9", PUBLISHED_FICHA_ID);
        setAuth("SCH_DELETE_DRAFT");
        mockMvc.perform(delete("/api/v1/schedules/{id}", id).param("row_version", "0"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("SCHEDULE_IMMUTABLE"));
    }

    @Test
    void rejectsGetWithOnlySchViewFicha() throws Exception {
        UUID id = createDraft(TEST_PERIOD_PREFIX + "10", TEST_FICHA_ID);
        // SCH_VIEW_FICHA no está contratado en HU-002/OpenAPI para este endpoint:
        // la negación ocurre antes de tocar el recurso (sin filtrar datos).
        // En producción (filtros activos) SecurityConfig lo traduce a 403 FORBIDDEN.
        setAuth("SCH_VIEW_FICHA");
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.servlet.ServletException.class,
            () -> mockMvc.perform(get("/api/v1/schedules/{id}", id)));
    }

    @Test
    void instructorSeesOnlyOwnSchedules() throws Exception {
        UUID slot = insertTimeSlot(SLOT_PREFIX + "A", 1);
        UUID scheduleA = insertScheduleWithSession(
            TEST_PERIOD_PREFIX + "11", TEST_FICHA_ID, INSTRUCTOR_A, slot, "PUBLISHED");
        UUID scheduleB = insertScheduleWithSession(
            TEST_PERIOD_PREFIX + "12", TEST_FICHA_ID, INSTRUCTOR_B, slot, "PUBLISHED");
        UUID draftSchedule = insertScheduleWithSession(
            TEST_PERIOD_PREFIX + "1D", TEST_FICHA_ID, INSTRUCTOR_A, slot, "DRAFT", "2099-01-12");

        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_SCHEDULE"), INSTRUCTOR_A, "SCH_VIEW_OWN");
        MvcResult result = mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains(scheduleA.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(scheduleB.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(draftSchedule.toString());

        // GET ajeno con OWN_SCHEDULE -> 404 (no revela existencia, guidelines §5).
        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleB))
            .andExpect(status().isNotFound());

        // GET propio -> 200.
        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleA))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schedules/{id}", draftSchedule))
            .andExpect(status().isNotFound());
    }

    @Test
    void learnerScopeForcesFichaAndIgnoresQueryParam() throws Exception {
        UUID ownSchedule = insertPublished(TEST_PERIOD_PREFIX + "1L", TEST_FICHA_ID);
        UUID otherSchedule = insertPublished(TEST_PERIOD_PREFIX + "2L", OTHER_FICHA_ID);
        UUID draftSchedule = createDraft(TEST_PERIOD_PREFIX + "3L", TEST_FICHA_ID);

        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_FICHA_AS_LEARNER"), null, TEST_FICHA_ID, "SCH_VIEW_OWN");
        MvcResult result = mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10")
                .param("ficha_id", OTHER_FICHA_ID.toString()))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains(ownSchedule.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(otherSchedule.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(draftSchedule.toString());

        mockMvc.perform(get("/api/v1/schedules/{id}", otherSchedule))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/schedules/{id}", ownSchedule))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/schedules/{id}", draftSchedule))
            .andExpect(status().isNotFound());
    }

    @Test
    void learnerScopeWithoutFichaClaimIsFailClosed() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_OWN:OWN_FICHA_AS_LEARNER"), null, "SCH_VIEW_OWN");
        mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void globalScopeSeesAll() throws Exception {
        UUID slot = insertTimeSlot(SLOT_PREFIX + "B", 2);
        insertDraftWithSession(TEST_PERIOD_PREFIX + "13", TEST_FICHA_ID, INSTRUCTOR_A, slot);
        insertDraftWithSession(TEST_PERIOD_PREFIX + "14", TEST_FICHA_ID, INSTRUCTOR_B, slot);

        setJwtAuth(List.of("SCH_VIEW_ALL:GLOBAL"), null, "SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10")
                .param("ficha_id", TEST_FICHA_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagination.total_items").value(2));
    }

    @Test
    void trainingCenterScopeIsAcceptedForViewAll() throws Exception {
        UUID slot = insertTimeSlot(SLOT_PREFIX + "BT", 2);
        UUID schedule = insertDraftWithSession(TEST_PERIOD_PREFIX + "1T", TEST_FICHA_ID, INSTRUCTOR_A, slot);
        mapFichaToTrainingCenter(TEST_FICHA_ID, TRAINING_CENTER_ID);

        setJwtAuth(List.of("SCH_VIEW_ALL:TRAINING_CENTER"), null, null, TRAINING_CENTER_ID, "SCH_VIEW_ALL");
        MvcResult result = mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10")
                .param("ficha_id", TEST_FICHA_ID.toString()))
            .andExpect(status().isOk())
            .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
            .contains(schedule.toString());

        mockMvc.perform(get("/api/v1/schedules/{id}", schedule))
            .andExpect(status().isOk());
    }

    @Test
    void trainingCenterScopeFiltersSchedulesByFichaCenter() throws Exception {
        UUID slot = insertTimeSlot(SLOT_PREFIX + "CT", 2);
        UUID ownSchedule = insertDraftWithSession(TEST_PERIOD_PREFIX + "2T", TEST_FICHA_ID, INSTRUCTOR_A, slot);
        UUID otherSchedule = insertDraftWithSession(TEST_PERIOD_PREFIX + "3T", OTHER_FICHA_ID, INSTRUCTOR_B, slot);
        mapFichaToTrainingCenter(TEST_FICHA_ID, TRAINING_CENTER_ID);
        mapFichaToTrainingCenter(OTHER_FICHA_ID, OTHER_TRAINING_CENTER_ID);

        setJwtAuth(List.of("SCH_VIEW_ALL:TRAINING_CENTER"), null, null, TRAINING_CENTER_ID, "SCH_VIEW_ALL");
        MvcResult result = mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isOk())
            .andReturn();
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains(ownSchedule.toString());
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(otherSchedule.toString());

        mockMvc.perform(get("/api/v1/schedules/{id}", otherSchedule))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/schedules/{id}", ownSchedule))
            .andExpect(status().isOk());
    }

    @Test
    void trainingCenterScopeWithoutClaimIsFailClosed() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_ALL:TRAINING_CENTER"), null, "SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void publishesUnderReviewStagesOutboxAndArchivesReplacement() throws Exception {
        String period = TEST_PERIOD_PREFIX + "15";
        UUID oldPublished = insertPublished(period, PUBLISHED_FICHA_ID);
        UUID slot = insertTimeSlot(SLOT_PREFIX + "C", 3);
        UUID replacement = insertUnderReviewWithSession(period, PUBLISHED_FICHA_ID, INSTRUCTOR_A, slot);

        setAuth("SCH_PUBLISH");
        mockMvc.perform(post("/api/v1/schedules/{id}/publish", replacement)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.published_by").value(TEST_USER_ID.toString()));

        String oldStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM scheduling.schedule WHERE id = ?", String.class, oldPublished);
        org.assertj.core.api.Assertions.assertThat(oldStatus).isEqualTo("ARCHIVED");

        Integer staged = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM scheduling.outbox
                WHERE event_type = 'scheduling.schedule.published'
                  AND status = 'PENDING'
                  AND payload->>'schedule_id' = ?
                  AND payload->>'ficha_id' = ?
                  AND payload->>'period' = ?
                  AND payload->>'session_count' = '1'
                  AND payload->>'published_by' = ?
                  AND payload->'instructor_ids' @> ?::jsonb
                  AND payload->'environment_ids' @> (
                    SELECT to_jsonb(ARRAY[environment_id::text])
                    FROM scheduling.class_session WHERE schedule_id = ? LIMIT 1
                  )
                """,
            Integer.class, replacement.toString(), PUBLISHED_FICHA_ID.toString(), period,
            TEST_USER_ID.toString(), "[\"" + INSTRUCTOR_A + "\"]", replacement);
        org.assertj.core.api.Assertions.assertThat(staged).isEqualTo(1);
    }

    @Test
    void publishIgnoresNonContractualPublishedByQueryParamAndUsesJwtActor() throws Exception {
        String period = TEST_PERIOD_PREFIX + "5B";
        UUID slot = insertTimeSlot(SLOT_PREFIX + "D", 3);
        UUID replacement = insertUnderReviewWithSession(period, PUBLISHED_FICHA_ID, INSTRUCTOR_A, slot);

        setAuth("SCH_PUBLISH");
        mockMvc.perform(post("/api/v1/schedules/{id}/publish", replacement)
                .param("publishedBy", SPOOFED_PUBLISHER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.published_by").value(TEST_USER_ID.toString()));

        UUID persistedPublisher = jdbcTemplate.queryForObject(
            "SELECT published_by FROM scheduling.schedule WHERE id = ?", UUID.class, replacement);
        org.assertj.core.api.Assertions.assertThat(persistedPublisher).isEqualTo(TEST_USER_ID);
    }

    @Test
    void publishRollsBackScheduleAndReplacementArchiveWhenOutboxStorageFails() throws Exception {
        String period = TEST_PERIOD_PREFIX + "5B";
        UUID oldPublished = insertPublished(period, PUBLISHED_FICHA_ID);
        UUID slot = insertTimeSlot(SLOT_PREFIX + "CB", 4);
        UUID replacement = insertUnderReviewWithSession(period, PUBLISHED_FICHA_ID, INSTRUCTOR_A, slot);

        installOutboxFailureTrigger();
        try {
            setAuth("SCH_PUBLISH");
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    mockMvc.perform(post("/api/v1/schedules/{id}/publish", replacement)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"row_version\": 0 }")))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                .hasStackTraceContaining("IT forced outbox failure");
        } finally {
            dropOutboxFailureTrigger();
        }

        String targetStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM scheduling.schedule WHERE id = ?", String.class, replacement);
        String previousStatus = jdbcTemplate.queryForObject(
            "SELECT status FROM scheduling.schedule WHERE id = ?", String.class, oldPublished);
        Integer staged = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM scheduling.outbox
                WHERE event_type = 'scheduling.schedule.published'
                  AND payload->>'schedule_id' = ?
                """, Integer.class, replacement.toString());

        org.assertj.core.api.Assertions.assertThat(targetStatus).isEqualTo("UNDER_REVIEW");
        org.assertj.core.api.Assertions.assertThat(previousStatus).isEqualTo("PUBLISHED");
        org.assertj.core.api.Assertions.assertThat(staged).isZero();
    }

    @Test
    void archivesPublishedScheduleManually() throws Exception {
        UUID id = insertPublished(TEST_PERIOD_PREFIX + "16", PUBLISHED_FICHA_ID);
        Instant originalUpdatedAt = jdbcTemplate.queryForObject(
            "SELECT updated_at FROM scheduling.schedule WHERE id = ?", Instant.class, id);
        setAuth("SCH_ARCHIVE");

        mockMvc.perform(post("/api/v1/schedules/{id}/archive", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 0 }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("ARCHIVED"))
            .andExpect(jsonPath("$.row_version").value(0));

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM scheduling.schedule WHERE id = ?", String.class, id);
        Instant archivedUpdatedAt = jdbcTemplate.queryForObject(
            "SELECT updated_at FROM scheduling.schedule WHERE id = ?", Instant.class, id);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("ARCHIVED");
        org.assertj.core.api.Assertions.assertThat(archivedUpdatedAt).isEqualTo(originalUpdatedAt);
    }

    @Test
    void rejectsArchiveWithStaleRowVersion() throws Exception {
        UUID id = insertPublished(TEST_PERIOD_PREFIX + "6B", PUBLISHED_FICHA_ID);
        setAuth("SCH_ARCHIVE");

        mockMvc.perform(post("/api/v1/schedules/{id}/archive", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"row_version\": 99 }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("ROW_VERSION_MISMATCH"));

        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM scheduling.schedule WHERE id = ?", String.class, id);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("PUBLISHED");
    }

    @Test
    void databaseTriggerRejectsPublishedUpdateAndDeleteExceptArchive() {
        UUID id = insertPublished(TEST_PERIOD_PREFIX + "17", PUBLISHED_FICHA_ID);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbcTemplate.update("UPDATE scheduling.schedule SET name = 'forbidden' WHERE id = ?", id))
            .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE id = ?", id))
            .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);

        jdbcTemplate.update("UPDATE scheduling.schedule SET status = 'ARCHIVED' WHERE id = ?", id);
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM scheduling.schedule WHERE id = ?", String.class, id);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("ARCHIVED");
    }

    @Test
    void unresolvableScopeIsFailClosed() throws Exception {
        setJwtAuth(List.of("SCH_VIEW_OWN:TRAINING_CENTER"), INSTRUCTOR_A, "SCH_VIEW_OWN");
        mockMvc.perform(get("/api/v1/schedules")
                .param("page", "1").param("page_size", "10"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private UUID createDraft(String period, UUID fichaId) throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "ficha_id": "%s", "period": "%s", "name": "it" }
                    """.formatted(fichaId, period)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private UUID insertPublished(String period, UUID fichaId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, name, status, row_version,
                     created_by, created_at, updated_at, is_active)
                VALUES (?, ?, ?, 'published-fixture', 'PUBLISHED', 0, ?, ?, ?, true)
                """,
            id, fichaId, period, TEST_USER_ID, now, now);
        return id;
    }

    private UUID insertTimeSlot(String name, int dayOfWeek) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO scheduling.time_slot
                    (id, name, day_of_week, start_time, end_time, shift, is_active)
                VALUES (?, ?, ?, '07:00:00', '10:00:00', 'DAY', true)
                """,
            id, name, dayOfWeek);
        return id;
    }

    private UUID insertDraftWithSession(String period, UUID fichaId, UUID instructorId, UUID slotId) {
        return insertScheduleWithSession(period, fichaId, instructorId, slotId, "DRAFT");
    }

    private UUID insertScheduleWithSession(String period, UUID fichaId, UUID instructorId, UUID slotId,
                                          String status) {
        return insertScheduleWithSession(period, fichaId, instructorId, slotId, status, "2099-01-05");
    }

    private UUID insertScheduleWithSession(String period, UUID fichaId, UUID instructorId, UUID slotId,
                                           String status, String sessionDate) {
        UUID scheduleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, name, status, row_version,
                     created_by, created_at, updated_at, is_active)
                VALUES (?, ?, ?, 'scope-fixture', ?, 0, ?, ?, ?, true)
                """,
            scheduleId, fichaId, period, status, TEST_USER_ID, now, now);
        jdbcTemplate.update("""
                INSERT INTO scheduling.class_session
                    (id, schedule_id, competency_id, environment_id, instructor_id,
                     time_slot_id, start_time, end_time, day_of_week, session_date,
                     status, updated_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, '07:00:00', '10:00:00', 1, ?, 'ACTIVE', ?, true)
                """,
            UUID.randomUUID(), scheduleId, UUID.randomUUID(), UUID.randomUUID(),
            instructorId, slotId, java.sql.Date.valueOf(sessionDate), now);
        return scheduleId;
    }

    private UUID insertUnderReviewWithSession(String period, UUID fichaId, UUID instructorId, UUID slotId) {
        UUID scheduleId = insertDraftWithSession(period, fichaId, instructorId, slotId);
        jdbcTemplate.update("UPDATE scheduling.schedule SET status = 'UNDER_REVIEW' WHERE id = ?", scheduleId);
        return scheduleId;
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

    private void installOutboxFailureTrigger() {
        dropOutboxFailureTrigger();
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION scheduling.it_fail_schedule_published_outbox()
            RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'IT forced outbox failure';
            END;
            $$ LANGUAGE plpgsql
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER trg_it_fail_schedule_published_outbox
            BEFORE INSERT ON scheduling.outbox
            FOR EACH ROW
            WHEN (NEW.event_type = 'scheduling.schedule.published')
            EXECUTE FUNCTION scheduling.it_fail_schedule_published_outbox()
            """);
    }

    private void dropOutboxFailureTrigger() {
        jdbcTemplate.execute(
            "DROP TRIGGER IF EXISTS trg_it_fail_schedule_published_outbox ON scheduling.outbox");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS scheduling.it_fail_schedule_published_outbox()");
    }
}
