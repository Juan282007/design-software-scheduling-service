package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.Schedule;
import java.util.UUID;

/**
 * Use case para publicar un horario (UNDER_REVIEW → PUBLISHED).
 */
public interface PublishScheduleUseCase {

    /**
     * Publica el horario si no tiene conflictos sin resolver.
     * @param scheduleId ID del horario
     * @param publishedBy ID del usuario que publica
     * @param rowVersion versión optimista para control de concurrencia
     */
    Schedule publish(UUID scheduleId, UUID publishedBy, Integer rowVersion);
}
