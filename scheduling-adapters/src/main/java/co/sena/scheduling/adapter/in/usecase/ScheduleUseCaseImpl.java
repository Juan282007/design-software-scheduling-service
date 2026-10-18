package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.ScheduleAccess;
import co.sena.scheduling.application.port.in.ScheduleFilter;
import co.sena.scheduling.application.port.in.SchedulePage;
import co.sena.scheduling.application.port.in.ScheduleUseCase;
import co.sena.scheduling.application.port.in.UpdateScheduleCommand;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.domain.exception.RowVersionMismatchException;
import co.sena.scheduling.domain.exception.ScheduleAccessDeniedException;
import co.sena.scheduling.domain.exception.ScheduleImmutableException;
import co.sena.scheduling.domain.exception.ScheduleNotFoundException;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.ScheduleStatus;
import co.sena.scheduling.domain.service.ScheduleDomainService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** HU-002 E2-E4: listar, obtener, editar (solo name) y eliminar (soft delete solo DRAFT). */
@Component
public class ScheduleUseCaseImpl implements ScheduleUseCase {

    private static final UUID SYSTEM_ACTOR_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PersistencePort persistencePort;
    private final ScheduleDomainService domainService;

    public ScheduleUseCaseImpl(PersistencePort persistencePort,
                               ScheduleDomainService domainService) {
        this.persistencePort = persistencePort;
        this.domainService = domainService;
    }

    @Override
    @Transactional(readOnly = true)
    public SchedulePage list(ScheduleFilter filter, ScheduleAccess access) {
        ScheduleAccess effective = access == null ? ScheduleAccess.unrestricted() : access;
        if (effective.getType() == ScheduleAccess.Type.DENIED) {
            throw new ScheduleAccessDeniedException(effective.getReason());
        }
        ScheduleFilter effectiveFilter = filter == null ? ScheduleFilter.defaults() : filter;
        if (effectiveFilter.getStatus() != null) {
            try {
                ScheduleStatus.valueOf(effectiveFilter.getStatus());
            } catch (IllegalArgumentException ex) {
                throw new ScheduleValidationException("status", "INVALID_VALUE",
                    "status debe ser DRAFT, UNDER_REVIEW, PUBLISHED o ARCHIVED");
            }
        }
        if (effective.getType() == ScheduleAccess.Type.OWNER_INSTRUCTOR) {
            if (effective.getInstructorId() == null) {
                throw new ScheduleAccessDeniedException("OWN_SCHEDULE sin actor_id en el JWT");
            }
            ScheduleFilter ownerFilter = new ScheduleFilter(
                effectiveFilter.getFichaId(),
                effectiveFilter.getPeriod(), "PUBLISHED",
                effectiveFilter.getFrom(), effectiveFilter.getTo(),
                effectiveFilter.getPage(), effectiveFilter.getPageSize(), effectiveFilter.getSort());
            java.util.List<UUID> visibleIds =
                persistencePort.findActiveScheduleIdsByInstructorId(effective.getInstructorId());
            if (visibleIds.isEmpty()) {
                return new SchedulePage(java.util.List.of(),
                    effectiveFilter.getPage(), effectiveFilter.getPageSize(), 0);
            }
            return persistencePort.search(ownerFilter, visibleIds);
        }
        if (effective.getType() == ScheduleAccess.Type.OWNER_FICHA) {
            if (effective.getFichaId() == null) {
                throw new ScheduleAccessDeniedException(
                    "OWN_FICHA_AS_LEARNER requiere ficha_id en el JWT");
            }
            ScheduleFilter ownerFilter = new ScheduleFilter(effective.getFichaId(),
                effectiveFilter.getPeriod(), "PUBLISHED",
                effectiveFilter.getFrom(), effectiveFilter.getTo(),
                effectiveFilter.getPage(), effectiveFilter.getPageSize(), effectiveFilter.getSort());
            return persistencePort.search(ownerFilter);
        }
        if (effective.getType() == ScheduleAccess.Type.TRAINING_CENTER) {
            if (effective.getTrainingCenterId() == null) {
                throw new ScheduleAccessDeniedException("TRAINING_CENTER sin training_center_id en el JWT");
            }
            return persistencePort.search(effectiveFilter, null, effective.getTrainingCenterId());
        }
        return persistencePort.search(effectiveFilter);
    }

    @Override
    @Transactional(readOnly = true)
    public Schedule get(UUID id, ScheduleAccess access) {
        ScheduleAccess effective = access == null ? ScheduleAccess.unrestricted() : access;
        if (effective.getType() == ScheduleAccess.Type.DENIED) {
            throw new ScheduleAccessDeniedException(effective.getReason());
        }
        Schedule schedule = findActive(id);
        if (effective.getType() == ScheduleAccess.Type.OWNER_INSTRUCTOR) {
            // 404 (no 403) para no revelar existencia fuera del scope (guidelines §5).
            if (effective.getInstructorId() == null
                || schedule.getStatus() != ScheduleStatus.PUBLISHED
                || !persistencePort.findActiveScheduleIdsByInstructorId(effective.getInstructorId())
                    .contains(schedule.getId())) {
                throw new ScheduleNotFoundException(id);
            }
        }
        if (effective.getType() == ScheduleAccess.Type.OWNER_FICHA
            && (effective.getFichaId() == null
                || schedule.getStatus() != ScheduleStatus.PUBLISHED
                || !effective.getFichaId().equals(schedule.getFichaId()))) {
            throw new ScheduleNotFoundException(id);
        }
        if (effective.getType() == ScheduleAccess.Type.TRAINING_CENTER
            && !persistencePort.fichaBelongsToTrainingCenter(schedule.getFichaId(), effective.getTrainingCenterId())) {
            throw new ScheduleNotFoundException(id);
        }
        return schedule;
    }

    @Override
    @Transactional
    public Schedule update(UUID id, UpdateScheduleCommand command) {
        if (command == null) {
            throw new ScheduleValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        Schedule schedule = findActive(id);
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ScheduleImmutableException("El horario no está en DRAFT y es inmutable");
        }
        if (schedule.getRowVersion() != command.getRowVersion()) {
            throw new RowVersionMismatchException(command.getRowVersion(), schedule.getRowVersion());
        }
        domainService.validateUpdateName(command.getName());
        if (command.getName() != null) {
            schedule.setName(command.getName().trim());
        }
        schedule.incrementRowVersion();
        schedule.setUpdatedAt(Instant.now());
        return persistencePort.save(schedule);
    }

    @Override
    @Transactional
    public void delete(UUID id, int rowVersion, UUID deletedBy) {
        if (id == null) {
            throw new ScheduleValidationException("id", "REQUIRED", "id es obligatorio");
        }
        Schedule schedule = findActive(id);
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ScheduleImmutableException("Solo se puede eliminar un horario en DRAFT");
        }
        if (schedule.getRowVersion() != rowVersion) {
            throw new RowVersionMismatchException(rowVersion, schedule.getRowVersion());
        }
        schedule.deactivate(Instant.now(), deletedBy == null ? SYSTEM_ACTOR_ID : deletedBy);
        schedule.setUpdatedAt(Instant.now());
        persistencePort.save(schedule);
    }

    private Schedule findActive(UUID id) {
        if (id == null) {
            throw new ScheduleValidationException("id", "REQUIRED", "id es obligatorio");
        }
        return persistencePort.findActiveById(id).orElseThrow(() -> new ScheduleNotFoundException(id));
    }
}
