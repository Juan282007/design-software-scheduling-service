package co.sena.scheduling.application.port.out;

import co.sena.scheduling.domain.model.EnvironmentAvailabilityProjection;
import co.sena.scheduling.domain.model.InstructorAvailabilityProjection;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-SCH-004: Puerto de persistencia para read models (hexagonal).
 */
public interface AvailabilityProjectionPort {

    // Environment
    Optional<EnvironmentAvailabilityProjection> findEnvironmentByEnvironmentId(UUID environmentId);
    EnvironmentAvailabilityProjection saveEnvironment(EnvironmentAvailabilityProjection projection);
    boolean existsByLastEventId(UUID eventId);

    // Instructor
    Optional<InstructorAvailabilityProjection> findInstructorByInstructorIdAndCompetencyId(UUID instructorId, UUID competencyId);
    InstructorAvailabilityProjection saveInstructor(InstructorAvailabilityProjection projection);
}
