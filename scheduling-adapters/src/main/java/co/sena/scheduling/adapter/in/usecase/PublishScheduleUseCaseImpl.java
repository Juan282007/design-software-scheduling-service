package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.PublishScheduleUseCase;
import co.sena.scheduling.application.port.out.OutboxStagingPort;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.application.port.out.SchedulingConflictPort;
import co.sena.scheduling.application.port.out.SessionPersistencePort;
import co.sena.scheduling.domain.exception.ScheduleNotFoundException;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.ClassSession;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.SchedulingConflict;
import co.sena.scheduling.domain.event.ScheduleEvents;
import co.sena.scheduling.domain.service.ConflictDetectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class PublishScheduleUseCaseImpl implements PublishScheduleUseCase {

    private final PersistencePort persistencePort;
    private final SessionPersistencePort sessionPort;
    private final SchedulingConflictPort conflictPort;
    private final ConflictDetectionService conflictDetectionService;
    private final OutboxStagingPort outboxStaging;
    private final ObjectMapper objectMapper;

    public PublishScheduleUseCaseImpl(PersistencePort persistencePort,
                                       SessionPersistencePort sessionPort,
                                       SchedulingConflictPort conflictPort,
                                       ConflictDetectionService conflictDetectionService,
                                       OutboxStagingPort outboxStaging,
                                       ObjectMapper objectMapper) {
        this.persistencePort = persistencePort;
        this.sessionPort = sessionPort;
        this.conflictPort = conflictPort;
        this.conflictDetectionService = conflictDetectionService;
        this.outboxStaging = outboxStaging;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Schedule publish(UUID scheduleId, UUID publishedBy, Integer rowVersion) {
        Optional<Schedule> optional = persistencePort.findById(scheduleId);
        if (optional.isEmpty()) {
            throw new ScheduleNotFoundException(scheduleId);
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

        // HU-SCH-005 E1.3 y E3: detección obligatoria antes de publicar, reconciliación
        // idempotente y bloqueo si hay conflictos bloqueantes sin resolver (409)
        List<ClassSession> sessions = sessionPort.findByScheduleId(scheduleId);
        List<SchedulingConflict> conflicts = conflictDetectionService.detectForSchedule(scheduleId, sessions);
        java.util.Set<UUID> scope = sessions.stream().map(ClassSession::getId)
                .collect(java.util.stream.Collectors.toSet());
        conflictPort.reconcileDetected(scheduleId, scope, conflicts);
        List<SchedulingConflict> blocking = conflictPort.findByScheduleIdAndBlocking(scheduleId, true).stream()
                .filter(c -> !c.isResolved()).toList();
        if (!blocking.isEmpty()) {
            throw new co.sena.scheduling.domain.exception.UnresolvedConflictsException(blocking.size());
        }

        // Reemplazo: el vigente del mismo ficha+period se archiva antes de publicar
        // para liberar el índice único parcial de PUBLISHED dentro de la transacción.
        persistencePort.archivePublishedByFichaIdAndPeriodExcept(
            schedule.getFichaId(), schedule.getPeriod(), schedule.getId());

        try {
            schedule.publish(publishedBy);
        } catch (IllegalStateException wrongState) {
            // Contrato: 409 SCHEDULE_IMMUTABLE cuando no está en UNDER_REVIEW
            throw new co.sena.scheduling.domain.exception.ScheduleImmutableException(wrongState.getMessage());
        }
        Schedule saved = persistencePort.save(schedule);

        List<UUID> instructorIds = sessions.stream()
            .map(ClassSession::getInstructorId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        List<UUID> environmentIds = sessions.stream()
            .map(ClassSession::getEnvironmentId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        ScheduleEvents.SchedulePublished event = new ScheduleEvents.SchedulePublished(
            saved.getId(), saved.getFichaId(), saved.getPeriod(), sessions.size(),
            publishedBy, saved.getPublishedAt(), instructorIds, environmentIds);
        outboxStaging.stage(UUID.randomUUID(), "scheduling.schedule.published", serialize(event));

        return saved;
    }

    private String serialize(ScheduleEvents.SchedulePublished event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schedule_id", event.getScheduleId());
            payload.put("ficha_id", event.getFichaId());
            payload.put("period", event.getPeriod());
            payload.put("session_count", event.getSessionCount());
            payload.put("published_by", event.getPublishedBy());
            payload.put("published_at", event.getPublishedAt());
            payload.put("instructor_ids", event.getInstructorIds());
            payload.put("environment_ids", event.getEnvironmentIds());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ScheduleValidationException("payload", "INVALID_JSON",
                "No fue posible serializar el evento de publicación");
        }
    }
}
