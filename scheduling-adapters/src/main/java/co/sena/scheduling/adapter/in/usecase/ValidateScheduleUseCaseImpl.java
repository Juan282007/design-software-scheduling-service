package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.ValidateScheduleUseCase;
import co.sena.scheduling.application.port.out.MessagingPort;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.application.port.out.SchedulingConflictPort;
import co.sena.scheduling.application.port.out.SessionPersistencePort;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.ClassSession;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.ScheduleStatus;
import co.sena.scheduling.domain.model.SchedulingConflict;
import co.sena.scheduling.domain.event.ScheduleEvents;
import co.sena.scheduling.domain.service.ConflictDetectionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ValidateScheduleUseCaseImpl implements ValidateScheduleUseCase {

    private final PersistencePort persistencePort;
    private final SessionPersistencePort sessionPort;
    private final SchedulingConflictPort conflictPort;
    private final ConflictDetectionService conflictDetectionService;
    private final MessagingPort messagingPort;

    public ValidateScheduleUseCaseImpl(PersistencePort persistencePort,
                                        SessionPersistencePort sessionPort,
                                        SchedulingConflictPort conflictPort,
                                        ConflictDetectionService conflictDetectionService,
                                        MessagingPort messagingPort) {
        this.persistencePort = persistencePort;
        this.sessionPort = sessionPort;
        this.conflictPort = conflictPort;
        this.conflictDetectionService = conflictDetectionService;
        this.messagingPort = messagingPort;
    }

    @Override
    public Schedule validate(UUID scheduleId, Integer rowVersion) {
        Optional<Schedule> optional = persistencePort.findById(scheduleId);
        if (optional.isEmpty()) {
            throw new RuntimeException("Schedule not found with id: " + scheduleId);
        }

        Schedule schedule = optional.get();

        // E1: contrato RBAC + row_version (409 ROW_VERSION_MISMATCH si no coincide)
        if (rowVersion == null) {
            throw new co.sena.scheduling.domain.exception.ScheduleValidationException(
                    "row_version", "REQUIRED", "row_version es obligatorio");
        }
        if (!rowVersion.equals(schedule.getRowVersion())) {
            throw new co.sena.scheduling.domain.exception.RowVersionMismatchException(
                    rowVersion, schedule.getRowVersion());
        }

        // HU-SCH-005 E1.2: detección completa del borrador antes de pasar a UNDER_REVIEW
        // + E3 reconciliación idempotente (sin duplicados ni reabrir resueltos)
        List<ClassSession> sessions = sessionPort.findByScheduleId(scheduleId);
        List<SchedulingConflict> conflicts = conflictDetectionService.detectForSchedule(scheduleId, sessions);
        java.util.Set<UUID> scope = sessions.stream().map(ClassSession::getId)
                .collect(java.util.stream.Collectors.toSet());
        conflictPort.reconcileDetected(scheduleId, scope, conflicts);

        try {
            schedule.validate();
        } catch (IllegalStateException wrongState) {
            // Contrato: 409 SCHEDULE_IMMUTABLE cuando no está en DRAFT
            throw new co.sena.scheduling.domain.exception.ScheduleImmutableException(wrongState.getMessage());
        }
        Schedule saved = persistencePort.save(schedule);

        messagingPort.publishScheduleValidated(
            new ScheduleEvents.ScheduleValidated(
                saved.getId(), saved.getFichaId(), saved.getPeriod(),
                java.time.Instant.now()
            )
        );

        return saved;
    }
}
