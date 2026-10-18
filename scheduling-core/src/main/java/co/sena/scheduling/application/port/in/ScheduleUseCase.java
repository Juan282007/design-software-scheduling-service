package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.Schedule;
import java.util.UUID;

/** Caso de uso HU-002 E2-E4: listar, obtener, editar y eliminar borrador. */
public interface ScheduleUseCase {

    SchedulePage list(ScheduleFilter filter, ScheduleAccess access);

    Schedule get(UUID id, ScheduleAccess access);

    Schedule update(UUID id, UpdateScheduleCommand command);

    void delete(UUID id, int rowVersion, UUID deletedBy);
}
