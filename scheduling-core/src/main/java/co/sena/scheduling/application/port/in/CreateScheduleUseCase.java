package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.Schedule;

/**
 * Use case para crear un borrador de horario.
 * Crea un Schedule con status=DRAFT.
 */
public interface CreateScheduleUseCase {

    /**
     * Crea un nuevo borrador de horario.
     * @return Schedule creado con DRAFT status
     */
    Schedule create(CreateScheduleCommand command);
}
