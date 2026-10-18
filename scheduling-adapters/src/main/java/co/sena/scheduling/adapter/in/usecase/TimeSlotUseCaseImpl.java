package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.CreateTimeSlotCommand;
import co.sena.scheduling.application.port.in.TimeSlotFilter;
import co.sena.scheduling.application.port.in.TimeSlotPage;
import co.sena.scheduling.application.port.in.TimeSlotUseCase;
import co.sena.scheduling.application.port.in.UpdateTimeSlotCommand;
import co.sena.scheduling.application.port.out.TimeSlotPersistencePort;
import co.sena.scheduling.domain.exception.TimeSlotInUseException;
import co.sena.scheduling.domain.exception.TimeSlotNotFoundException;
import co.sena.scheduling.domain.exception.TimeSlotValidationException;
import co.sena.scheduling.domain.model.ShiftType;
import co.sena.scheduling.domain.model.TimeSlot;
import co.sena.scheduling.domain.service.TimeSlotDomainService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Caso de uso de catálogo CRUD de franjas horarias. */
@Component
public class TimeSlotUseCaseImpl implements TimeSlotUseCase {

    private static final UUID SYSTEM_ACTOR_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final TimeSlotPersistencePort persistencePort;
    private final TimeSlotDomainService domainService;

    public TimeSlotUseCaseImpl(TimeSlotPersistencePort persistencePort,
                               TimeSlotDomainService domainService) {
        this.persistencePort = persistencePort;
        this.domainService = domainService;
    }

    @Override
    @Transactional
    public TimeSlot create(CreateTimeSlotCommand command) {
        if (command == null) {
            throw new TimeSlotValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        ShiftType shift = domainService.normalizeShift(command.getShift());
        domainService.validate(command.getName(), command.getDayOfWeek(),
            command.getStartTime(), command.getEndTime(), shift);

        TimeSlot timeSlot = new TimeSlot(
            UUID.randomUUID(), command.getName().trim(), command.getDayOfWeek(),
            command.getStartTime(), command.getEndTime(), shift);
        timeSlot.setActive(true);
        return persistencePort.save(timeSlot);
    }

    @Override
    @Transactional
    public TimeSlot update(UUID id, UpdateTimeSlotCommand command) {
        if (command == null) {
            throw new TimeSlotValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        TimeSlot timeSlot = findActive(id);
        ShiftType shift = domainService.normalizeShift(command.getShift());
        domainService.validate(command.getName(), command.getDayOfWeek(),
            command.getStartTime(), command.getEndTime(), shift);

        timeSlot.setName(command.getName().trim());
        timeSlot.setDayOfWeek(command.getDayOfWeek());
        timeSlot.setStartTime(command.getStartTime());
        timeSlot.setEndTime(command.getEndTime());
        timeSlot.setShift(shift);
        return persistencePort.save(timeSlot);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeSlot get(UUID id) {
        return findActive(id);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeSlotPage list(TimeSlotFilter filter) {
        TimeSlotFilter effectiveFilter = filter == null ? TimeSlotFilter.defaults() : filter;
        if (effectiveFilter.getPage() < 1) {
            throw new TimeSlotValidationException("page", "OUT_OF_RANGE", "page debe ser mayor o igual a 1");
        }
        if (effectiveFilter.getPageSize() < 1 || effectiveFilter.getPageSize() > 100) {
            throw new TimeSlotValidationException("page_size", "OUT_OF_RANGE", "page_size debe estar entre 1 y 100");
        }
        if (effectiveFilter.getDayOfWeek() != null
                && (effectiveFilter.getDayOfWeek() < 1 || effectiveFilter.getDayOfWeek() > 7)) {
            throw new TimeSlotValidationException("day_of_week", "OUT_OF_RANGE", "day_of_week debe estar entre 1 y 7");
        }
        return persistencePort.search(effectiveFilter);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID deletedBy) {
        if (id == null) {
            throw new TimeSlotValidationException("id", "REQUIRED", "id es obligatorio");
        }
        // Atómico (hallazgo QA e-2): una sola UPDATE protege contra la carrera
        // check-referencia → desactivación; la reclasificación posterior es solo lectura.
        int updated = persistencePort.deactivateIfUnused(
            id, Instant.now(), deletedBy == null ? SYSTEM_ACTOR_ID : deletedBy);
        if (updated == 0) {
            if (persistencePort.findActiveById(id).isEmpty()) {
                throw new TimeSlotNotFoundException(id);
            }
            throw new TimeSlotInUseException(id);
        }
    }

    private TimeSlot findActive(UUID id) {
        if (id == null) {
            throw new TimeSlotValidationException("id", "REQUIRED", "id es obligatorio");
        }
        return persistencePort.findActiveById(id).orElseThrow(() -> new TimeSlotNotFoundException(id));
    }
}
