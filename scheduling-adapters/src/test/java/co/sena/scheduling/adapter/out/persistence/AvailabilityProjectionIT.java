package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.EnvironmentAvailabilityProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.entity.InstructorAvailabilityProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.repository.EnvironmentAvailabilityProjectionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.InstructorAvailabilityProjectionRepository;
import co.sena.scheduling.application.port.out.AvailabilityProjectionPort;
import co.sena.scheduling.adapter.in.messaging.EnvironmentEventsConsumer;
import co.sena.scheduling.adapter.out.persistence.AffectedSessionAdapter;
import co.sena.scheduling.application.port.out.AffectedSessionPort;
import co.sena.scheduling.domain.service.AvailabilityProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnabledIf("hasSchedulingDatabaseEnvironment")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AvailabilityProjectionIT.JpaTestConfiguration.class)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.default_schema=scheduling",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class AvailabilityProjectionIT {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredValue("SCHEDULING_DB_URL"));
        registry.add("spring.datasource.username", () -> requiredValue("SCHEDULING_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredValue("SCHEDULING_DB_PASSWORD"));
    }

    private static String requiredValue(String name) {
        String systemValue = System.getProperty(name);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        throw new IllegalStateException(name + " is required to run this integration test");
    }

    static boolean hasSchedulingDatabaseEnvironment() {
        return hasValue("SCHEDULING_DB_URL")
            && hasValue("SCHEDULING_DB_USERNAME")
            && hasValue("SCHEDULING_DB_PASSWORD");
    }

    private static boolean hasValue(String name) {
        String systemValue = System.getProperty(name);
        String environmentValue = System.getenv(name);
        return (systemValue != null && !systemValue.isBlank())
            || (environmentValue != null && !environmentValue.isBlank());
    }

    @Autowired
    private EnvironmentAvailabilityProjectionRepository envRepo;
    @Autowired
    private InstructorAvailabilityProjectionRepository instructorRepo;
    @Autowired
    private JdbcTemplate jdbc;
    private EnvironmentEventsConsumer consumer;

    private AvailabilityProjectionPort port;
    private AffectedSessionPort affectedPort;
    private AvailabilityProjectionService service;
    // Consumer in conflict-validator; test parses envelope directly

    @BeforeEach
    void setUp() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS scheduling.environment_availability_projection (
                id UUID PRIMARY KEY,
                environment_id UUID NOT NULL UNIQUE,
                available BOOLEAN NOT NULL,
                effective_from TIMESTAMPTZ,
                reason TEXT,
                last_event_id UUID NOT NULL UNIQUE,
                updated_at TIMESTAMPTZ NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS scheduling.instructor_availability_projection (
                id UUID PRIMARY KEY,
                instructor_id UUID NOT NULL,
                competency_id UUID,
                available BOOLEAN,
                last_event_id UUID NOT NULL UNIQUE,
                updated_at TIMESTAMPTZ NOT NULL,
                UNIQUE (instructor_id, competency_id)
            )""");
        // Ensure base tables exist for session tests
        jdbc.execute("DELETE FROM scheduling.environment_availability_projection");
        jdbc.execute("DELETE FROM scheduling.instructor_availability_projection");
        // Clean sessions with test period
        try { jdbc.update("DELETE FROM scheduling.class_session WHERE schedule_id IN (SELECT id FROM scheduling.schedule WHERE period = '2099-99')"); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM scheduling.schedule WHERE period = '2099-99'"); } catch (Exception ignored) {}

        port = new AvailabilityProjectionAdapter(envRepo, instructorRepo);
        affectedPort = new AffectedSessionAdapter(jdbc);
        service = new AvailabilityProjectionService(port, affectedPort);
        consumer = new EnvironmentEventsConsumer(service);
    }

    private String envelope(String event, UUID eventId, String dataJson) {
        return """
            {
              "event": "%s",
              "version": "1.0",
              "source": "training-environment-service",
              "event_id": "%s",
              "timestamp": "2026-06-22T00:00:00Z",
              "data": %s
            }
            """.formatted(event, eventId, dataJson);
    }

    @Test
    void e1_idempotencia_viaConsumer_conEnvelope() {
        UUID eventId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        String data = """
            {"environment_id": "%s", "available": false, "effective_from": "2026-06-21T00:00:00Z", "reason": "cierre"}
            """.formatted(envId);
        String json = envelope("environment.availability.changed", eventId, data);

        boolean first = consumer.handle(json);
        boolean second = consumer.handle(json); // mismo event_id

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(envRepo.count()).isEqualTo(1);
    }

    @Test
    void e2_maintenance_identificaSesionesFuturasACTIVE_sinCancelar() {
        // Crear schedule + time_slot + class_session futura ACTIVE
        UUID scheduleId = UUID.randomUUID();
        UUID timeSlotId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO scheduling.time_slot (id, name, day_of_week, start_time, end_time, shift, is_active) VALUES (?, ?, 1, ?, ?, 'DAY', true)",
            timeSlotId, "IT slot", Time.valueOf("07:00:00"), Time.valueOf("10:00:00"));
        jdbc.update("INSERT INTO scheduling.schedule (id, ficha_id, period, status, created_by, created_at, updated_at) VALUES (?, ?, '2099-99', 'DRAFT', ?, ?, ?)",
            scheduleId, UUID.randomUUID(), UUID.randomUUID(), now, now);
        jdbc.update("""
            INSERT INTO scheduling.class_session
                (id, schedule_id, competency_id, environment_id, instructor_id, time_slot_id, start_time, end_time, day_of_week, session_date, status, updated_at, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, 'ACTIVE', ?, true)
            """,
            sessionId, scheduleId, UUID.randomUUID(), envId, UUID.randomUUID(), timeSlotId,
            Time.valueOf("07:00:00"), Time.valueOf("10:00:00"), Date.valueOf("2026-06-23"), now);

        // Enviar maintenance que cubre la sesión
        UUID eventId = UUID.randomUUID();
        String data = """
            {"environment_id": "%s", "maintenance_id": "%s", "start_date": "2026-06-22", "end_date": "2026-06-25", "description": "electrico"}
            """.formatted(envId, UUID.randomUUID());
        String json = envelope("environment.maintenance.started", eventId, data);

        boolean applied = consumer.handle(json);
        assertThat(applied).isTrue();

        // Verificar proyección marcada no disponible
        EnvironmentAvailabilityProjectionEntity proj = envRepo.findByEnvironmentId(envId).orElseThrow();
        assertThat(proj.isAvailable()).isFalse();
        assertThat(proj.getReason()).contains("electrico");

        // Verificar que la sesión futura ACTIVE fue identificada por el código de producción (E2) — no solo por el test
        List<UUID> affected = affectedPort.findActiveSessionIdsByEnvironmentIdAndDateRange(envId, LocalDate.of(2026,6,22), LocalDate.of(2026,6,25));
        assertThat(affected).contains(sessionId);

        // Verificar que no se canceló automáticamente (E2: cancelación es HU-SCH-003)
        String status = jdbc.queryForObject("SELECT status FROM scheduling.class_session WHERE id = ?", String.class, sessionId);
        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void e4_envelopeDocumentado_esRequerido() {
        UUID eventId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        // Envelope incompleto debe fallar
        String badJson = "{\"event\": \"environment.availability.changed\", \"data\": {}}";
        try {
            consumer.handle(badJson);
            assertThat(false).as("debería fallar con envelope inválido").isTrue();
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Invalid envelope");
        }
        // Envelope correcto con todos los campos documentados debe pasar
        String goodData = """
            {"environment_id": "%s", "available": true, "effective_from": "2026-06-21T00:00:00Z", "reason": "reapertura"}
            """.formatted(envId);
        String goodJson = envelope("environment.availability.changed", eventId, goodData);
        assertThat(consumer.handle(goodJson)).isTrue();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = {EnvironmentAvailabilityProjectionRepository.class, InstructorAvailabilityProjectionRepository.class})
    @EntityScan(basePackageClasses = {EnvironmentAvailabilityProjectionEntity.class, InstructorAvailabilityProjectionEntity.class})


    static class JpaTestConfiguration {}
}
