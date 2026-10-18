package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.ClassSession;
import java.util.UUID;

/** Caso de uso HU-003: sesiones del borrador (E1-E5). */
public interface SessionUseCase {

    ClassSession create(CreateSessionCommand command, UUID createdBy);

    SessionPage list(SessionFilter filter, ScheduleAccess access);

    ClassSession get(UUID id, ScheduleAccess access);

    ClassSession update(UUID id, UpdateSessionCommand command);

    ClassSession cancel(UUID id);

    void delete(UUID id, UUID deletedBy);
}
