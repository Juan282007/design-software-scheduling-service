package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.ArchiveScheduleUseCase;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.domain.exception.RowVersionMismatchException;
import co.sena.scheduling.domain.exception.ScheduleImmutableException;
import co.sena.scheduling.domain.exception.ScheduleNotFoundException;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.Schedule;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class ArchiveScheduleUseCaseImpl implements ArchiveScheduleUseCase {

    private final PersistencePort persistencePort;

    public ArchiveScheduleUseCaseImpl(PersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Override
    @Transactional
    public Schedule archive(UUID scheduleId, Integer rowVersion) {
        if (scheduleId == null) {
            throw new ScheduleValidationException("id", "REQUIRED", "id es obligatorio");
        }
        if (rowVersion == null) {
            throw new ScheduleValidationException("row_version", "REQUIRED", "row_version es obligatorio");
        }
        Schedule schedule = persistencePort.findActiveById(scheduleId)
            .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        if (schedule.getRowVersion() != rowVersion) {
            throw new RowVersionMismatchException(rowVersion, schedule.getRowVersion());
        }
        try {
            schedule.archive();
        } catch (IllegalStateException wrongState) {
            throw new ScheduleImmutableException(wrongState.getMessage());
        }
        return persistencePort.save(schedule);
    }
}
