package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.ClassSessionEntity;
import co.sena.scheduling.adapter.out.persistence.entity.ScheduleEntity;
import co.sena.scheduling.adapter.out.persistence.entity.TimeSlotEntity;
import co.sena.scheduling.adapter.out.persistence.repository.ClassSessionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.TimeSlotRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EnabledIf("hasSchedulingDatabaseEnvironment")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TimeSlotRepositoryIT.JpaTestConfiguration.class)
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.default_schema=scheduling",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class TimeSlotRepositoryIT {

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
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            return value;
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
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndHidesSoftDeletedTimeSlots() {
        UUID id = UUID.randomUUID();
        TimeSlotEntity entity = validEntity(id);

        timeSlotRepository.saveAndFlush(entity);

        assertThat(timeSlotRepository.findByIdAndIsActiveTrue(id)).isPresent();

        entity.setIsActive(false);
        entity.setDeletedAt(Instant.now());
        entity.setDeletedBy(UUID.randomUUID());
        timeSlotRepository.saveAndFlush(entity);

        assertThat(timeSlotRepository.findByIdAndIsActiveTrue(id)).isEmpty();
    }

    @Test
    void detectsOnlyActiveClassSessionsReferencingTheTimeSlot() {
        UUID timeSlotId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        TimeSlotEntity timeSlot = validEntity(timeSlotId);
        timeSlotRepository.saveAndFlush(timeSlot);

        insertSchedule(scheduleId);
        insertClassSession(sessionId, scheduleId, timeSlotId, "ACTIVE", true);

        assertThat(classSessionRepository.existsByTimeSlotIdAndStatusAndIsActiveTrue(timeSlotId, "ACTIVE"))
            .isTrue();

        jdbcTemplate.update("UPDATE scheduling.class_session SET status = 'CANCELLED' WHERE id = ?", sessionId);

        assertThat(classSessionRepository.existsByTimeSlotIdAndStatusAndIsActiveTrue(timeSlotId, "ACTIVE"))
            .isFalse();
    }

    private TimeSlotEntity validEntity(UUID id) {
        TimeSlotEntity entity = new TimeSlotEntity();
        entity.setId(id);
        entity.setName("IT time slot " + id);
        entity.setDayOfWeek(1);
        entity.setStartTime(LocalTime.of(7, 0));
        entity.setEndTime(LocalTime.of(10, 0));
        entity.setShift("NIGHT");
        entity.setIsActive(true);
        return entity;
    }

    private void insertSchedule(UUID scheduleId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO scheduling.schedule
                    (id, ficha_id, period, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, 'DRAFT', ?, ?, ?)
                """,
            scheduleId, UUID.randomUUID(), "2099-1", UUID.randomUUID(), now, now);
    }

    private void insertClassSession(UUID sessionId, UUID scheduleId, UUID timeSlotId,
                                     String status, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO scheduling.class_session
                    (id, schedule_id, competency_id, environment_id, instructor_id,
                     time_slot_id, start_time, end_time, day_of_week, session_date,
                     status, notes, updated_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            sessionId, scheduleId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            timeSlotId, Time.valueOf("07:00:00"), Time.valueOf("10:00:00"), 1,
            Date.valueOf("2099-01-01"), status, null, Timestamp.from(Instant.now()), active);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = {TimeSlotRepository.class, ClassSessionRepository.class})
    @EntityScan(basePackageClasses = {TimeSlotEntity.class, ClassSessionEntity.class, ScheduleEntity.class})
    static class JpaTestConfiguration {
    }
}
