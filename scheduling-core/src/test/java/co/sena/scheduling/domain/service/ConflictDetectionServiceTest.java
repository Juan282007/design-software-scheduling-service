package co.sena.scheduling.domain.service;

import co.sena.scheduling.application.port.out.EnvironmentAvailabilityProjectionPort;
import co.sena.scheduling.application.port.out.InstructorAvailabilityProjectionPort;
import co.sena.scheduling.application.port.out.SiteTravelTimePort;
import co.sena.scheduling.domain.model.ClassSession;
import co.sena.scheduling.domain.model.ConflictType;
import co.sena.scheduling.domain.model.EnvironmentAvailabilityProjection;
import co.sena.scheduling.domain.model.InstructorAvailabilityProjection;
import co.sena.scheduling.domain.model.SchedulingConflict;
import co.sena.scheduling.domain.model.SessionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-SCH-005 E2: los 6 tipos de conflicto en la detección aplicativa.
 */
class ConflictDetectionServiceTest {

    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final UUID COMPETENCY_ID = UUID.randomUUID();
    private static final UUID SLOT_ID = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.of(2099, 1, 4);
    private static final LocalDate TUESDAY = LocalDate.of(2099, 1, 5);

    private final UUID instructorA = UUID.randomUUID();
    private final UUID instructorB = UUID.randomUUID();
    private final UUID instructorC = UUID.randomUUID();
    private final UUID environmentA = UUID.randomUUID();
    private final UUID environmentB = UUID.randomUUID();

    /** Read models HU-004 simulados: se habilitan por prueba. */
    private final Set<UUID> unavailableEnvironments = new java.util.HashSet<>();
    private final Set<UUID> unavailableInstructors = new java.util.HashSet<>();

    private final EnvironmentAvailabilityProjectionPort envPort = new EnvironmentAvailabilityProjectionPort() {
        @Override
        public Optional<EnvironmentAvailabilityProjection> findByEnvironmentId(UUID environmentId) {
            return Optional.empty();
        }

        @Override
        public boolean isEnvironmentUnavailable(UUID environmentId, LocalDate date) {
            return unavailableEnvironments.contains(environmentId);
        }
    };

    private final InstructorAvailabilityProjectionPort instructorPort = new InstructorAvailabilityProjectionPort() {
        @Override
        public Optional<InstructorAvailabilityProjection> findByInstructorIdAndCompetencyId(UUID instructorId, UUID competencyId) {
            return Optional.empty();
        }

        @Override
        public boolean isInstructorUnavailable(UUID instructorId, UUID competencyId) {
            return unavailableInstructors.contains(instructorId);
        }
    };

    private final SiteTravelTimePort travelPort = (origin, destination) ->
            environmentA.equals(origin) && environmentB.equals(destination)
                    ? Optional.of(45) : Optional.empty();

    private final ConflictDetectionService service =
            new ConflictDetectionService(envPort, instructorPort, travelPort);

    private ClassSession session(UUID id, UUID instructor, UUID environment,
                                 LocalDate date, String start, String end) {
        ClassSession s = new ClassSession(SCHEDULE_ID, COMPETENCY_ID, environment, instructor,
                SLOT_ID, date, LocalTime.parse(start), LocalTime.parse(end),
                date.getDayOfWeek().getValue());
        s.setId(id);
        return s;
    }

    private List<ConflictType> typesOf(List<SchedulingConflict> conflicts) {
        return conflicts.stream().map(SchedulingConflict::getConflictType).toList();
    }

    @Test
    void detectsInstructorDoubleBookedWhenSameInstructorOverlaps() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(second, instructorA, environmentB, MONDAY, "09:00", "12:00"),
                List.of(session(first, instructorA, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(typesOf(conflicts))
                .contains(ConflictType.INSTRUCTOR_DOUBLE_BOOKED, ConflictType.SESSIONS_OVERLAP);
        assertThat(conflicts).noneMatch(c -> c.getConflictType() == ConflictType.ENVIRONMENT_DOUBLE_BOOKED);
    }

    @Test
    void detectsEnvironmentDoubleBookedWhenSameEnvironmentOverlaps() {
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(UUID.randomUUID(), instructorB, environmentA, MONDAY, "09:00", "12:00"),
                List.of(session(UUID.randomUUID(), instructorA, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(typesOf(conflicts))
                .containsExactlyInAnyOrder(ConflictType.ENVIRONMENT_DOUBLE_BOOKED, ConflictType.SESSIONS_OVERLAP);
    }

    @Test
    void detectsSessionsOverlapWhenDifferentInstructorAndEnvironment() {
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(UUID.randomUUID(), instructorB, environmentB, MONDAY, "08:00", "11:00"),
                List.of(session(UUID.randomUUID(), instructorA, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(typesOf(conflicts)).containsExactly(ConflictType.SESSIONS_OVERLAP);
    }

    @Test
    void detectsEnvironmentMaintenanceFromProjection() {
        unavailableEnvironments.add(environmentA);
        UUID only = UUID.randomUUID();
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(only, instructorA, environmentA, MONDAY, "07:00", "10:00"), List.of());

        assertThat(typesOf(conflicts)).containsExactly(ConflictType.ENVIRONMENT_MAINTENANCE);
        SchedulingConflict conflict = conflicts.get(0);
        assertThat(conflict.getSessionAId()).isEqualTo(only);
        assertThat(conflict.getSessionBId()).isNull();
    }

    @Test
    void detectsInstructorUnavailableFromProjection() {
        unavailableInstructors.add(instructorB);
        UUID only = UUID.randomUUID();
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(only, instructorB, environmentB, MONDAY, "07:00", "10:00"), List.of());

        assertThat(typesOf(conflicts)).containsExactly(ConflictType.INSTRUCTOR_UNAVAILABLE);
        assertThat(conflicts.get(0).getSessionBId()).isNull();
    }

    @Test
    void detectsInstructorTravelConflictWhenGapIsShorterThanTravelTime() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(second, instructorA, environmentB, MONDAY, "10:15", "12:15"),
                List.of(session(first, instructorA, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(typesOf(conflicts)).containsExactly(ConflictType.INSTRUCTOR_TRAVEL_CONFLICT);
        assertThat(conflicts.get(0).getSessionAId()).isEqualTo(second);
        assertThat(conflicts.get(0).getSessionBId()).isEqualTo(first);
    }

    @Test
    void doesNotDetectTravelConflictWhenGapCoversTravelTime() {
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(UUID.randomUUID(), instructorA, environmentB, MONDAY, "10:45", "12:45"),
                List.of(session(UUID.randomUUID(), instructorA, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void doesNotDetectTravelConflictForDifferentInstructors() {
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(UUID.randomUUID(), instructorC, environmentB, MONDAY, "10:15", "12:15"),
                List.of(session(UUID.randomUUID(), instructorA, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void doesNotDetectTimeOverlapAcrossDifferentDates() {
        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(UUID.randomUUID(), instructorA, environmentB, TUESDAY, "07:00", "10:00"),
                List.of(session(UUID.randomUUID(), instructorB, environmentA, MONDAY, "07:00", "10:00")));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void ignoresCancelledSessions() {
        ClassSession cancelled = session(UUID.randomUUID(), instructorA, environmentA,
                MONDAY, "07:00", "10:00");
        cancelled.setStatus(SessionStatus.CANCELLED);

        List<SchedulingConflict> conflicts = service.detectForNewSession(SCHEDULE_ID,
                session(UUID.randomUUID(), instructorC, environmentB, MONDAY, "08:00", "11:00"),
                List.of(cancelled));

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectForScheduleCoversAllSixConflictTypes() {
        unavailableEnvironments.add(environmentA);
        unavailableInstructors.add(instructorB);
        // 4 sesiones que ejercitan los 6 tipos en una sola corrida (E2).
        List<ClassSession> sessions = List.of(
                // mantenimiento de ambiente + instructor no disponible
                session(UUID.randomUUID(), instructorB, environmentA, MONDAY, "07:00", "10:00"),
                // misma ambiente que s1 (solape) e mismo instructor que s3 (sin solape)
                session(UUID.randomUUID(), instructorA, environmentA, MONDAY, "09:00", "11:00"),
                // mismo instructor que s2 pero traslado sin tiempo suficiente
                session(UUID.randomUUID(), instructorA, environmentB, MONDAY, "11:30", "13:30"),
                // doble-asignación de instructor y ambiente contra s2
                session(UUID.randomUUID(), instructorA, environmentA, MONDAY, "09:30", "11:30"));

        List<SchedulingConflict> detected = service.detectForSchedule(SCHEDULE_ID, sessions);
        List<ConflictType> types = typesOf(detected);

        assertThat(types).contains(
                ConflictType.ENVIRONMENT_MAINTENANCE,
                ConflictType.INSTRUCTOR_UNAVAILABLE,
                ConflictType.INSTRUCTOR_DOUBLE_BOOKED,
                ConflictType.ENVIRONMENT_DOUBLE_BOOKED,
                ConflictType.SESSIONS_OVERLAP,
                ConflictType.INSTRUCTOR_TRAVEL_CONFLICT);
        // Un conflicto simple por sesión afectada, no uno por par (E3).
        assertThat(conflictsOfType(detected, ConflictType.ENVIRONMENT_MAINTENANCE)).hasSize(3);
        assertThat(conflictsOfType(detected, ConflictType.INSTRUCTOR_UNAVAILABLE)).hasSize(1);
    }

    private List<SchedulingConflict> conflictsOfType(List<SchedulingConflict> conflicts, ConflictType type) {
        return conflicts.stream().filter(c -> c.getConflictType() == type).toList();
    }

    @Test
    void conflictKeyIsIndependentOfSessionOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        String direct = ConflictDetectionService.conflictKey("SESSIONS_OVERLAP", a, b);
        String reversed = ConflictDetectionService.conflictKey("SESSIONS_OVERLAP", b, a);
        String single = ConflictDetectionService.conflictKey("ENVIRONMENT_MAINTENANCE", a, null);

        assertThat(direct).isEqualTo(reversed);
        assertThat(single).isNotEqualTo(direct);
        assertThat(ConflictDetectionService.conflictKey("INSTRUCTOR_DOUBLE_BOOKED", a, b))
                .isNotEqualTo(direct);
    }

    @Test
    void unresolvedPairAndSingleConflictsAreDistinctKeys() {
        UUID session = UUID.randomUUID();
        Set<String> keys = Set.of(
                ConflictDetectionService.conflictKey("ENVIRONMENT_MAINTENANCE", session, null),
                ConflictDetectionService.conflictKey("INSTRUCTOR_UNAVAILABLE", session, null),
                ConflictDetectionService.conflictKey("SESSIONS_OVERLAP", session, UUID.randomUUID()));

        assertThat(keys).hasSize(3);
    }
}
