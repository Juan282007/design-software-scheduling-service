package co.sena.scheduling.application.port.out;

import co.sena.scheduling.domain.model.InstructorAvailabilityProjection;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto para consultar la proyección de disponibilidad de instructores (HU-004).
 * Usada por HU-SCH-005 E2 para detectar INSTRUCTOR_UNAVAILABLE.
 */
public interface InstructorAvailabilityProjectionPort {

    Optional<InstructorAvailabilityProjection> findByInstructorIdAndCompetencyId(UUID instructorId, UUID competencyId);

    /**
     * true si existe una regla que marque al instructor no disponible: para la
     * competencia dada (si competencyId no es nulo) o de forma general
     * (registro sin competencia).
     */
    boolean isInstructorUnavailable(UUID instructorId, UUID competencyId);
}
