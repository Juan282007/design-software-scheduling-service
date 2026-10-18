package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.TimeSlotEntity;
import co.sena.scheduling.adapter.out.persistence.repository.ClassSessionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.TimeSlotRepository;
import co.sena.scheduling.application.port.in.TimeSlotFilter;
import co.sena.scheduling.application.port.in.TimeSlotPage;
import co.sena.scheduling.application.port.out.TimeSlotPersistencePort;
import co.sena.scheduling.domain.exception.TimeSlotValidationException;
import co.sena.scheduling.domain.model.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del catálogo de franjas horarias. */
@Component
public class TimeSlotPersistenceAdapter implements TimeSlotPersistencePort {

    private static final Map<String, String> SORT_FIELDS = Map.of(
        "name", "name",
        "day_of_week", "dayOfWeek",
        "start_time", "startTime",
        "end_time", "endTime",
        "shift", "shift"
    );

    private final TimeSlotRepository timeSlotRepository;
    private final ClassSessionRepository classSessionRepository;

    public TimeSlotPersistenceAdapter(TimeSlotRepository timeSlotRepository,
                                      ClassSessionRepository classSessionRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.classSessionRepository = classSessionRepository;
    }

    @Override
    @Transactional
    public TimeSlot save(TimeSlot timeSlot) {
        TimeSlotEntity saved = timeSlotRepository.save(toEntity(timeSlot));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TimeSlot> findActiveById(UUID id) {
        return timeSlotRepository.findByIdAndIsActiveTrue(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public TimeSlotPage search(TimeSlotFilter filter) {
        PageRequest pageRequest = PageRequest.of(
            filter.getPage() - 1,
            filter.getPageSize(),
            resolveSort(filter.getSort())
        );
        String shift = filter.getShift() == null ? null : filter.getShift().name();
        Page<TimeSlotEntity> page = timeSlotRepository.searchActive(
            filter.getDayOfWeek(), shift, pageRequest);

        return new TimeSlotPage(
            page.getContent().stream().map(this::toDomain).toList(),
            filter.getPage(),
            filter.getPageSize(),
            page.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveClassSession(UUID timeSlotId) {
        return classSessionRepository.existsByTimeSlotIdAndStatusAndIsActiveTrue(timeSlotId, "ACTIVE");
    }

    @Override
    public int deactivateIfUnused(UUID id, Instant deletedAt, UUID deletedBy) {
        return timeSlotRepository.deactivateIfUnused(id, deletedAt, deletedBy);
    }

    private Sort resolveSort(String sortExpression) {
        if (sortExpression == null || sortExpression.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "dayOfWeek");
        }

        String[] tokens = sortExpression.trim().split(":", 2);
        String field = SORT_FIELDS.get(tokens[0]);
        if (field == null) {
            throw new TimeSlotValidationException(
                "sort", "UNSUPPORTED_FIELD", "El campo de ordenamiento no está permitido");
        }
        Sort.Direction direction = tokens.length == 2 && "desc".equalsIgnoreCase(tokens[1])
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
        if (tokens.length == 2 && !"asc".equalsIgnoreCase(tokens[1])
                && !"desc".equalsIgnoreCase(tokens[1])) {
            throw new TimeSlotValidationException(
                "sort", "INVALID_DIRECTION", "La dirección debe ser asc o desc");
        }
        return Sort.by(direction, field);
    }

    private TimeSlotEntity toEntity(TimeSlot timeSlot) {
        TimeSlotEntity entity = new TimeSlotEntity();
        entity.setId(timeSlot.getId());
        entity.setName(timeSlot.getName());
        entity.setDayOfWeek(timeSlot.getDayOfWeek());
        entity.setStartTime(timeSlot.getStartTime());
        entity.setEndTime(timeSlot.getEndTime());
        entity.setShift(timeSlot.getShift().name());
        entity.setIsActive(timeSlot.isActive());
        entity.setDeletedAt(timeSlot.getDeletedAt());
        entity.setDeletedBy(timeSlot.getDeletedBy());
        return entity;
    }

    private TimeSlot toDomain(TimeSlotEntity entity) {
        TimeSlot timeSlot = new TimeSlot();
        timeSlot.setId(entity.getId());
        timeSlot.setName(entity.getName());
        timeSlot.setDayOfWeek(entity.getDayOfWeek());
        timeSlot.setStartTime(entity.getStartTime());
        timeSlot.setEndTime(entity.getEndTime());
        timeSlot.setShift(co.sena.scheduling.domain.model.ShiftType.valueOf(entity.getShift()));
        timeSlot.setActive(entity.isActive());
        timeSlot.setDeletedAt(entity.getDeletedAt());
        timeSlot.setDeletedBy(entity.getDeletedBy());
        return timeSlot;
    }
}
