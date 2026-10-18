package co.sena.scheduling.application.port.out;

import co.sena.scheduling.domain.model.EnvironmentAvailabilityProjection;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto para consultar la proyección de disponibilidad de ambientes (HU-004).
 * Usada por HU-SCH-005 E2 para detectar ENVIRONMENT_MAINTENANCE.
 */
public interface EnvironmentAvailabilityProjectionPort {

    Optional<EnvironmentAvailabilityProjection> findByEnvironmentId(UUID environmentId);

    /**
     * true si el ambiente está marcado no disponible y su efectividad ya aplica
     * en la fecha dada (effective_from nulo o anterior al cierre de esa fecha).
     */
    boolean isEnvironmentUnavailable(UUID environmentId, LocalDate date);
}
