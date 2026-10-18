package co.sena.scheduling.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.sena.scheduling.api.SchedulesApiApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = SchedulesApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.default_schema=scheduling",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.task.scheduling.enabled=false",
    "outbox.enabled=false"
})
class TimeSlotControllerIT {

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

    private static final String TEST_NAME_PREFIX = "IT-HU-SCH-001-";
    private static final UUID ACTIVE_SESSION_TIME_SLOT_ID =
        UUID.fromString("a1000000-0000-4000-8000-000000000001");
    private static final UUID ACTIVE_SESSION_SCHEDULE_ID =
        UUID.fromString("a1000000-0000-4000-8000-000000000002");
    private static final UUID ACTIVE_SESSION_ID =
        UUID.fromString("a1000000-0000-4000-8000-000000000003");
    private static final UUID TEST_USER_ID =
        UUID.fromString("b2000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM scheduling.class_session WHERE id = ?", ACTIVE_SESSION_ID);
        jdbcTemplate.update("DELETE FROM scheduling.schedule WHERE id = ?", ACTIVE_SESSION_SCHEDULE_ID);
        jdbcTemplate.update("DELETE FROM scheduling.time_slot WHERE name LIKE ?", TEST_NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM scheduling.time_slot WHERE id = ?", ACTIVE_SESSION_TIME_SLOT_ID);
    }

    @BeforeEach
    void setupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth(String... features) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(features)
            .map(SimpleGrantedAuthority::new)
            .toList();
        // Decisión 17: auth.getName() es el `sub` del JWT (UUID de usuario emitido por iam-service).
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(TEST_USER_ID.toString(), null, authorities));
    }

    @Test
    void createsAndNormalizesATimeSlot() throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "IT-HU-SCH-001-Morning",
                      "day_of_week": 2,
                      "start_time": "07:00",
                      "end_time": "10:00",
                      "shift": "NIGHT"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("IT-HU-SCH-001-Morning"))
            .andExpect(jsonPath("$.day_of_week").value(2))
            .andExpect(jsonPath("$.start_time").value("07:00"))
            .andExpect(jsonPath("$.end_time").value("10:00"))
            .andExpect(jsonPath("$.shift").value("NIGHT"))
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(response.get("id").asText());
        Integer active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.time_slot WHERE id = ? AND is_active = true", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(active).isEqualTo(1);
    }

    @Test
    void listsTimeSlotsWithPaginationAndFilters() throws Exception {
        createTimeSlot("IT-HU-SCH-001-Night", 3, "NIGHT");
        setAuth("SCH_VIEW_ALL");

        mockMvc.perform(get("/api/v1/time-slots")
                .param("page", "1")
                .param("page_size", "10")
                .param("day_of_week", "3")
                .param("shift", "NIGHT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("IT-HU-SCH-001-Night"))
            .andExpect(jsonPath("$.pagination.page").value(1))
            .andExpect(jsonPath("$.pagination.page_size").value(10))
            .andExpect(jsonPath("$.pagination.total_items").value(1));
    }

    @Test
    void getsAndUpdatesATimeSlot() throws Exception {
        UUID id = createTimeSlot("IT-HU-SCH-001-Update", 2, "DAY");
        setAuth("SCH_VIEW_ALL");

        mockMvc.perform(get("/api/v1/time-slots/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.shift").value("DAY"));

        setAuth("SCH_EDIT");
        mockMvc.perform(put("/api/v1/time-slots/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "IT-HU-SCH-001-Updated",
                      "day_of_week": 5,
                      "start_time": "08:00",
                      "end_time": "12:00",
                      "shift": "MIXED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("IT-HU-SCH-001-Updated"))
            .andExpect(jsonPath("$.day_of_week").value(5))
            .andExpect(jsonPath("$.start_time").value("08:00"))
            .andExpect(jsonPath("$.end_time").value("12:00"))
            .andExpect(jsonPath("$.shift").value("MIXED"));
    }

    @Test
    void rejectsAnInvalidTimeRange() throws Exception {
        setAuth("SCH_CREATE");
        mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "IT-HU-SCH-001-Invalid",
                      "day_of_week": 1,
                      "start_time": "10:00",
                      "end_time": "07:00",
                      "shift": "DAY"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsConflictWhenDeletingATimeSlotReferencedByAnActiveSession() throws Exception {
        insertActiveSessionFixture();
        setAuth("SCH_EDIT");

        mockMvc.perform(delete("/api/v1/time-slots/{id}", ACTIVE_SESSION_TIME_SLOT_ID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("TIME_SLOT_IN_USE"));
    }

    @Test
    void performsSoftDelete() throws Exception {
        UUID id = createTimeSlot("IT-HU-SCH-001-Delete", 4, "MIXED");
        setAuth("SCH_EDIT");

        mockMvc.perform(delete("/api/v1/time-slots/{id}", id))
            .andExpect(status().isNoContent());

        Integer inactive = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM scheduling.time_slot WHERE id = ? AND is_active = false", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(inactive).isEqualTo(1);

        UUID deletedBy = jdbcTemplate.queryForObject(
            "SELECT deleted_by FROM scheduling.time_slot WHERE id = ?", UUID.class, id);
        org.assertj.core.api.Assertions.assertThat(deletedBy).isEqualTo(TEST_USER_ID);

        setAuth("SCH_VIEW_ALL");
        mockMvc.perform(get("/api/v1/time-slots/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void sortsByFieldAndDirectionPerContract() throws Exception {
        createTimeSlot("IT-HU-SCH-001-Sort-Mon", 1, "DAY");
        createTimeSlot("IT-HU-SCH-001-Sort-Fri", 5, "DAY");

        setAuth("SCH_VIEW_ALL");
        MvcResult result = mockMvc.perform(get("/api/v1/time-slots")
                .param("page", "1").param("page_size", "100")
                .param("sort", "day_of_week:desc"))
            .andExpect(status().isOk())
            .andReturn();

        List<Integer> daysOfOurRows = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(result.getResponse().getContentAsString()).get("data")) {
            if (row.get("name").asText().startsWith("IT-HU-SCH-001-Sort")) {
                daysOfOurRows.add(row.get("day_of_week").asInt());
            }
        }
        org.assertj.core.api.Assertions.assertThat(daysOfOurRows).containsExactly(5, 1);
    }

    @Test
    void returns400WithContractEnvelopeForMalformedTimes() throws Exception {
        setAuth("SCH_CREATE");
        mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "IT-HU-SCH-001-BadTime",
                      "day_of_week": 2,
                      "start_time": "25:99",
                      "end_time": "10:00",
                      "shift": "DAY"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void rejectsShiftValuesOutsideTheContractEnum() throws Exception {
        setAuth("SCH_CREATE");
        mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "IT-HU-SCH-001-LowerShift",
                      "day_of_week": 2,
                      "start_time": "07:00",
                      "end_time": "10:00",
                      "shift": "night"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private UUID createTimeSlot(String name, int dayOfWeek, String shift) throws Exception {
        setAuth("SCH_CREATE");
        MvcResult result = mockMvc.perform(post("/api/v1/time-slots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "day_of_week": %d,
                      "start_time": "07:00",
                      "end_time": "10:00",
                      "shift": "%s"
                    }
                    """.formatted(name, dayOfWeek, shift)))
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
            .get("id").asText());
    }

    private void insertActiveSessionFixture() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO scheduling.time_slot
                    (id, name, day_of_week, start_time, end_time, shift)
                VALUES (?, ?, 1, ?, ?, 'DAY')
                """,
            ACTIVE_SESSION_TIME_SLOT_ID, TEST_NAME_PREFIX + "Referenced",
            Time.valueOf("07:00:00"), Time.valueOf("10:00:00"));
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, status, created_by, created_at, updated_at)
                VALUES (?, ?, '2099-1', 'DRAFT', ?, ?, ?)
                """,
            ACTIVE_SESSION_SCHEDULE_ID, UUID.randomUUID(), UUID.randomUUID(), now, now);
        jdbcTemplate.update("""
                INSERT INTO scheduling.class_session
                    (id, schedule_id, competency_id, environment_id, instructor_id,
                     time_slot_id, start_time, end_time, day_of_week, session_date,
                     status, notes, updated_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'ACTIVE', NULL, ?, true)
                """,
            ACTIVE_SESSION_ID, ACTIVE_SESSION_SCHEDULE_ID, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), ACTIVE_SESSION_TIME_SLOT_ID, Time.valueOf("07:00:00"),
            Time.valueOf("10:00:00"), Date.valueOf("2099-01-01"), now);
    }
}
