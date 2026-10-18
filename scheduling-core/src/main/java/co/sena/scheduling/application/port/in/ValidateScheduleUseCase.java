package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.Schedule;
import java.util.UUID;

/**
 * Use case para validar un horario y pasarlo a UNDER_REVIEW.
 */
public interface ValidateScheduleUseCase {

    /**
     * Valida el horario y lo transiciona a UNDER_REVIEW.
     * @param scheduleId ID del horario
     * @param rowVersion versión optimista para control de concurrencia
     */
    Schedule validate(UUID scheduleId, Integer rowVersion);
}
