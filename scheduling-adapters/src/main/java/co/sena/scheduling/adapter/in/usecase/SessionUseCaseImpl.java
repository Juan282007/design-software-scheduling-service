package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.CreateSessionCommand;
import co.sena.scheduling.application.port.in.ScheduleAccess;
import co.sena.scheduling.application.port.in.SessionFilter;
import co.sena.scheduling.application.port.in.SessionPage;
import co.sena.scheduling.application.port.in.SessionUseCase;
import co.sena.scheduling.application.port.in.UpdateSessionCommand;
import co.sena.scheduling.application.port.out.MessagingPort;
import co.sena.scheduling.application.port.out.OutboxStagingPort;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.application.port.out.SessionPersistencePort;
import co.sena.scheduling.application.port.out.TimeSlotPersistencePort;
import co.sena.scheduling.domain.event.ScheduleEvents;
import co.sena.scheduling.domain.exception.ScheduleAccessDeniedException;
import co.sena.scheduling.domain.exception.ScheduleImmutableException;
import co.sena.scheduling.domain.exception.ScheduleNotFoundException;
import co.sena.scheduling.domain.exception.SessionConflictException;
import co.sena.scheduling.domain.exception.SessionNotFoundException;
import co.sena.scheduling.domain.exception.SessionValidationException;
import co.sena.scheduling.domain.exception.TimeSlotNotFoundException;
import co.sena.scheduling.domain.model.ClassSession;
import co.sena.scheduling.domain.model.ConflictType;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.ScheduleStatus;
import co.sena.scheduling.domain.model.SchedulingConflict;
import co.sena.scheduling.domain.model.SessionStatus;
import co.sena.scheduling.domain.model.TimeSlot;
import co.sena.scheduling.domain.service.ConflictDetectionService;
import co.sena.scheduling.domain.service.ScheduleDomainService;
import co.sena.scheduling.domain.service.SessionDomainService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HU-003: sesiones del borrador (E1-E5). */
@Component
public class SessionUseCaseImpl implements SessionUseCase {

    private static final UUID SYSTEM_ACTOR_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final SessionPersistencePort sessionPort;
    private final PersistencePort schedulePort;
    private final TimeSlotPersistencePort timeSlotPort;
    private final SessionDomainService domainService;
    private final ScheduleDomainService scheduleDomainService;
    private final ConflictDetectionService conflictDetectionService;
    private final co.sena.scheduling.application.port.out.SchedulingConflictPort conflictPort;
    private final MessagingPort messagingPort;
    private final OutboxStagingPort outboxStaging;

    public SessionUseCaseImpl(SessionPersistencePort sessionPort,
                              PersistencePort schedulePort,
                              TimeSlotPersistencePort timeSlotPort,
                              SessionDomainService domainService,
                              ScheduleDomainService scheduleDomainService,
                              ConflictDetectionService conflictDetectionService,
                              co.sena.scheduling.application.port.out.SchedulingConflictPort conflictPort,
                              MessagingPort messagingPort,
                              OutboxStagingPort outboxStaging) {
        this.sessionPort = sessionPort;
        this.schedulePort = schedulePort;
        this.timeSlotPort = timeSlotPort;
        this.domainService = domainService;
        this.scheduleDomainService = scheduleDomainService;
        this.conflictDetectionService = conflictDetectionService;
        this.conflictPort = conflictPort;
        this.messagingPort = messagingPort;
        this.outboxStaging = outboxStaging;
    }

    @Override
    @Transactional
    public ClassSession create(CreateSessionCommand command, UUID createdBy) {
        if (command == null) {
            throw new SessionValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        domainService.validateReferences(command.getCompetencyId(), command.getEnvironmentId(),
            command.getInstructorId(), command.getTimeSlotId(), command.getSessionDate());
        Schedule schedule = activeDraft(command.getScheduleId());
        TimeSlot slot = timeSlotPort.findActiveById(command.getTimeSlotId())
            .orElseThrow(() -> new TimeSlotNotFoundException(command.getTimeSlotId()));
        domainService.validateSlotWindow(slot, command.getSessionDate());

        ClassSession session = new ClassSession(
            schedule.getId(), command.getCompetencyId(), command.getEnvironmentId(),
            command.getInstructorId(), slot.getId(), command.getSessionDate(),
            slot.getStartTime(), slot.getEndTime(), slot.getDayOfWeek());
        session.setId(UUID.randomUUID());
        session.setNotes(command.getNotes() == null ? null : command.getNotes());
        session.setActive(true);

        try {
            ClassSession saved = sessionPort.save(session);
            // HU-SCH-005 E1.1: detección inmediata al guardar sesión + E3 reconciliación idempotente.
            // E6: los errores de detección NO se tragan — la detección aplicativa es fuente de verdad.
            List<ClassSession> existing = sessionPort.findByScheduleId(schedule.getId());
            List<SchedulingConflict> conflicts =
                conflictDetectionService.detectForNewSession(schedule.getId(), saved, existing);
            conflictPort.reconcileDetected(schedule.getId(), java.util.Set.of(saved.getId()), conflicts);
            messagingPort.publishClassSessionCreated(new ScheduleEvents.ClassSessionCreated(
                saved.getId(), saved.getScheduleId(), saved.getCompetencyId(),
                saved.getInstructorId(), saved.getEnvironmentId(), saved.getTimeSlotId(),
                saved.getSessionDate().toString()));
            return saved;
        } catch (DataIntegrityViolationException race) {
            throw toDoubleBooking(race);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SessionPage list(SessionFilter filter, ScheduleAccess access) {
        ScheduleAccess effective = access == null ? ScheduleAccess.unrestricted() : access;
        if (effective.getType() == ScheduleAccess.Type.DENIED) {
            throw new ScheduleAccessDeniedException(effective.getReason());
        }
        UUID owner = effective.getType() == ScheduleAccess.Type.OWNER_INSTRUCTOR
            ? effective.getInstructorId() : null;
        if (effective.getType() == ScheduleAccess.Type.OWNER_INSTRUCTOR && owner == null) {
            throw new ScheduleAccessDeniedException("OWN_SCHEDULE sin actor_id en el JWT");
        }
        UUID ownerFicha = effective.getType() == ScheduleAccess.Type.OWNER_FICHA
            ? effective.getFichaId() : null;
        if (effective.getType() == ScheduleAccess.Type.OWNER_FICHA && ownerFicha == null) {
            throw new ScheduleAccessDeniedException("OWN_FICHA_AS_LEARNER requiere ficha_id en el JWT");
        }
        UUID trainingCenter = effective.getType() == ScheduleAccess.Type.TRAINING_CENTER
            ? effective.getTrainingCenterId() : null;
        if (effective.getType() == ScheduleAccess.Type.TRAINING_CENTER && trainingCenter == null) {
            throw new ScheduleAccessDeniedException("TRAINING_CENTER sin training_center_id en el JWT");
        }
        return sessionPort.search(filter, owner, ownerFicha, trainingCenter);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassSession get(UUID id, ScheduleAccess access) {
        ScheduleAccess effective = access == null ? ScheduleAccess.unrestricted() : access;
        if (effective.getType() == ScheduleAccess.Type.DENIED) {
            throw new ScheduleAccessDeniedException(effective.getReason());
        }
        ClassSession session = findActive(id);
        if (effective.getType() == ScheduleAccess.Type.OWNER_INSTRUCTOR) {
            Schedule schedule = schedulePort.findActiveById(session.getScheduleId())
                .orElseThrow(() -> new SessionNotFoundException(id));
            if (effective.getInstructorId() == null
                || schedule.getStatus() != ScheduleStatus.PUBLISHED
                || !effective.getInstructorId().equals(session.getInstructorId())) {
                throw new SessionNotFoundException(id);
            }
        }
        if (effective.getType() == ScheduleAccess.Type.OWNER_FICHA) {
            Schedule schedule = schedulePort.findActiveById(session.getScheduleId())
                .orElseThrow(() -> new SessionNotFoundException(id));
            if (effective.getFichaId() == null
                || schedule.getStatus() != ScheduleStatus.PUBLISHED
                || !effective.getFichaId().equals(schedule.getFichaId())) {
                throw new SessionNotFoundException(id);
            }
        }
        if (effective.getType() == ScheduleAccess.Type.TRAINING_CENTER) {
            Schedule schedule = schedulePort.findActiveById(session.getScheduleId())
                .orElseThrow(() -> new SessionNotFoundException(id));
            if (!schedulePort.fichaBelongsToTrainingCenter(schedule.getFichaId(), effective.getTrainingCenterId())) {
                throw new SessionNotFoundException(id);
            }
        }
        return session;
    }

    @Override
    @Transactional
    public ClassSession update(UUID id, UpdateSessionCommand command) {
        if (command == null) {
            throw new SessionValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        ClassSession session = findActive(id);
        activeDraft(session.getScheduleId());

        if (command.getInstructorId() != null) {
            session.setInstructorId(command.getInstructorId());
        }
        if (command.getEnvironmentId() != null) {
            session.setEnvironmentId(command.getEnvironmentId());
        }
        if (command.getTimeSlotId() != null && !command.getTimeSlotId().equals(session.getTimeSlotId())) {
            TimeSlot slot = timeSlotPort.findActiveById(command.getTimeSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException(command.getTimeSlotId()));
            domainService.validateSlotWindow(slot, session.getSessionDate());
            session.setTimeSlotId(slot.getId());
            session.setStartTime(slot.getStartTime());
            session.setEndTime(slot.getEndTime());
            session.setDayOfWeek(slot.getDayOfWeek());
        }
        if (command.isNotesPresent()) {
            session.setNotes(command.getNotes());
        }
        session.setUpdatedAt(Instant.now());

        ClassSession saved;
        try {
            saved = sessionPort.save(session);
        } catch (DataIntegrityViolationException race) {
            throw toDoubleBooking(race);
        }

        // HU-SCH-005 E1: detección de conflictos al actualizar sesión (PUT) + E3 reconciliación.
        // E6: los errores de detección NO se tragan.
        List<ClassSession> existing = sessionPort.findByScheduleId(saved.getScheduleId());
        List<SchedulingConflict> conflicts = conflictDetectionService.detectForNewSession(
                saved.getScheduleId(), saved, existing);
        conflictPort.reconcileDetected(saved.getScheduleId(), java.util.Set.of(saved.getId()), conflicts);
        return saved;
    }

    @Override
    @Transactional
    public ClassSession cancel(UUID id) {
        ClassSession session = findActive(id);
        activeDraft(session.getScheduleId());
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new SessionConflictException("SESSION_ALREADY_CANCELLED",
                "La sesión ya está cancelada");
        }
        session.cancel();
        ClassSession saved = sessionPort.save(session);
        // Mínimo E5 local: stage PENDING en la misma transacción (relay: HU-007).
        outboxStaging.stage(saved.getId(), "scheduling.class_session.cancelled",
            "{\"session_id\":\"" + saved.getId() + "\",\"schedule_id\":\"" + saved.getScheduleId() + "\"}");
        messagingPort.publishClassSessionCancelled(new ScheduleEvents.ClassSessionCancelled(
            saved.getId(), saved.getScheduleId(), Instant.now()));
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID deletedBy) {
        ClassSession session = findActive(id);
        activeDraft(session.getScheduleId());
        session.deactivate(Instant.now(), deletedBy == null ? SYSTEM_ACTOR_ID : deletedBy);
        session.setUpdatedAt(Instant.now());
        sessionPort.save(session);
    }

    private Schedule activeDraft(UUID scheduleId) {
        if (scheduleId == null) {
            throw new SessionValidationException("schedule_id", "REQUIRED", "schedule_id es obligatorio");
        }
        Schedule schedule = schedulePort.findActiveById(scheduleId)
            .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ScheduleImmutableException("El horario no está en DRAFT y es inmutable");
        }
        if (!scheduleDomainService.canAddSession(schedule)) {
            throw new ScheduleImmutableException("Solo un horario DRAFT admite sesiones");
        }
        return schedule;
    }

    private ClassSession findActive(UUID id) {
        if (id == null) {
            throw new SessionValidationException("id", "REQUIRED", "id es obligatorio");
        }
        return sessionPort.findActiveById(id).orElseThrow(() -> new SessionNotFoundException(id));
    }

    /** E2: traduce la violación del EXCLUDE al 409 correspondiente. */
    private SessionConflictException toDoubleBooking(DataIntegrityViolationException ex) {
        return SessionConstraintMapper.toConflict(ex);
    }
}
