package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.Schedule;

import java.util.UUID;

/** Use case para archivar un horario publicado (PUBLISHED -> ARCHIVED). */
public interface ArchiveScheduleUseCase {

    Schedule archive(UUID scheduleId, Integer rowVersion);
}
