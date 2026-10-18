package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.application.port.in.ScheduleFilter;
import co.sena.scheduling.application.port.in.SchedulePage;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.adapter.out.persistence.entity.FichaTrainingCenterProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.entity.ScheduleEntity;
import co.sena.scheduling.adapter.out.persistence.repository.ClassSessionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.FichaTrainingCenterProjectionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.ScheduleRepository;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PersistenceAdapter implements PersistencePort {

    private final ScheduleRepository scheduleRepository;
    private final ClassSessionRepository classSessionRepository;
    private final FichaTrainingCenterProjectionRepository fichaTrainingCenterRepository;

    public PersistenceAdapter(ScheduleRepository scheduleRepository,
                              ClassSessionRepository classSessionRepository,
                              FichaTrainingCenterProjectionRepository fichaTrainingCenterRepository) {
        this.scheduleRepository = scheduleRepository;
        this.classSessionRepository = classSessionRepository;
        this.fichaTrainingCenterRepository = fichaTrainingCenterRepository;
    }

    @Override
    public Schedule save(Schedule schedule) {
        ScheduleEntity entity = toEntity(schedule);
        ScheduleEntity saved = scheduleRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Schedule> findById(UUID id) {
        return scheduleRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Schedule> findByFichaId(UUID fichaId) {
        return scheduleRepository.findByFichaId(fichaId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Schedule> findByFichaIdAndPeriod(UUID fichaId, String period) {
        return scheduleRepository.findByFichaIdAndPeriod(fichaId, period).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsPublishedByFichaIdAndPeriod(UUID fichaId, String period) {
        return scheduleRepository.existsByFichaIdAndPeriodAndStatus(fichaId, period, "PUBLISHED");
    }

    @Override
    public Optional<Schedule> findPublishedByFichaIdAndPeriod(UUID fichaId, String period) {
        return scheduleRepository.findPublishedByFichaIdAndPeriod(fichaId, period).map(this::toDomain);
    }

    @Override
    public int archivePublishedByFichaIdAndPeriodExcept(UUID fichaId, String period, UUID scheduleId) {
        return scheduleRepository.archivePublishedByFichaIdAndPeriodExcept(fichaId, period, scheduleId);
    }

    @Override
    public List<UUID> findActiveScheduleIdsByInstructorId(UUID instructorId) {
        return classSessionRepository.findActiveScheduleIdsByInstructorId(instructorId);
    }

    @Override
    public Optional<Schedule> findActiveById(UUID id) {
        return scheduleRepository.findByIdAndIsActiveTrue(id).map(this::toDomain);
    }

    @Override
    public SchedulePage search(ScheduleFilter filter) {
        return search(filter, null, null);
    }

    @Override
    public SchedulePage search(ScheduleFilter filter, List<UUID> visibleIds) {
        return search(filter, visibleIds, null);
    }

    @Override
    public SchedulePage search(ScheduleFilter filter, List<UUID> visibleIds, UUID trainingCenterId) {
        ScheduleFilter effective = filter == null ? ScheduleFilter.defaults() : filter;
        if (effective.getPage() < 1) {
            throw new ScheduleValidationException("page", "OUT_OF_RANGE", "page debe ser mayor o igual a 1");
        }
        if (effective.getPageSize() < 1 || effective.getPageSize() > 100) {
            throw new ScheduleValidationException("page_size", "OUT_OF_RANGE", "page_size debe estar entre 1 y 100");
        }
        PageRequest pageRequest = PageRequest.of(
            effective.getPage() - 1, effective.getPageSize(), resolveSort(effective.getSort()));
        // Specifications: solo se agregan los predicados con valor (evita el
        // "could not determine data type" de Postgres con Instant nulos en JPQL).
        Specification<ScheduleEntity> spec = Specification
            .where((root, query, cb) -> cb.isTrue(root.get("isActive")));
        if (effective.getFichaId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("fichaId"), effective.getFichaId()));
        }
        if (effective.getPeriod() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("period"), effective.getPeriod()));
        }
        if (effective.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), effective.getStatus()));
        }
        if (effective.getFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), effective.getFrom()));
        }
        if (effective.getTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), effective.getTo()));
        }
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                return new SchedulePage(List.of(),
                    effective.getPage(), effective.getPageSize(), 0);
            }
            spec = spec.and((root, query, cb) -> root.get("id").in(visibleIds));
        }
        if (trainingCenterId != null) {
            spec = spec.and((root, query, cb) -> {
                var subquery = query.subquery(UUID.class);
                var projection = subquery.from(FichaTrainingCenterProjectionEntity.class);
                subquery.select(projection.get("fichaId"))
                    .where(
                        cb.equal(projection.get("fichaId"), root.get("fichaId")),
                        cb.equal(projection.get("trainingCenterId"), trainingCenterId)
                    );
                return cb.exists(subquery);
            });
        }
        Page<ScheduleEntity> page = scheduleRepository.findAll(spec, pageRequest);
        return new SchedulePage(
            page.getContent().stream().map(this::toDomain).toList(),
            effective.getPage(), effective.getPageSize(), page.getTotalElements());
    }

    @Override
    public boolean fichaBelongsToTrainingCenter(UUID fichaId, UUID trainingCenterId) {
        if (fichaId == null || trainingCenterId == null) {
            return false;
        }
        return fichaTrainingCenterRepository.existsByFichaIdAndTrainingCenterId(fichaId, trainingCenterId);
    }

    private Sort resolveSort(String sortExpression) {
        if (sortExpression == null || sortExpression.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        String[] tokens = sortExpression.trim().split(":", 2);
        String field = switch (tokens[0]) {
            case "created_at" -> "createdAt";
            case "updated_at" -> "updatedAt";
            case "period" -> "period";
            case "status" -> "status";
            default -> throw new ScheduleValidationException(
                "sort", "UNSUPPORTED_FIELD", "El campo de ordenamiento no está permitido");
        };
        Sort.Direction direction = tokens.length == 2 && "desc".equalsIgnoreCase(tokens[1])
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (tokens.length == 2 && !"asc".equalsIgnoreCase(tokens[1]) && !"desc".equalsIgnoreCase(tokens[1])) {
            throw new ScheduleValidationException(
                "sort", "INVALID_DIRECTION", "La dirección debe ser asc o desc");
        }
        return Sort.by(direction, field);
    }

    private ScheduleEntity toEntity(Schedule schedule) {
        ScheduleEntity entity = new ScheduleEntity();
        entity.setId(schedule.getId());
        entity.setFichaId(schedule.getFichaId());
        entity.setPeriod(schedule.getPeriod());
        entity.setName(schedule.getName());
        entity.setStatus(schedule.getStatus().name());
        entity.setPublishedAt(schedule.getPublishedAt());
        entity.setPublishedBy(schedule.getPublishedBy());
        entity.setRowVersion(schedule.getRowVersion());
        entity.setCreatedBy(schedule.getCreatedBy());
        entity.setCreatedAt(schedule.getCreatedAt());
        entity.setUpdatedAt(schedule.getUpdatedAt());
        entity.setIsActive(schedule.isActive());
        entity.setDeletedAt(schedule.getDeletedAt());
        entity.setDeletedBy(schedule.getDeletedBy());
        return entity;
    }

    private Schedule toDomain(ScheduleEntity entity) {
        Schedule schedule = new Schedule();
        schedule.setId(entity.getId());
        schedule.setFichaId(entity.getFichaId());
        schedule.setPeriod(entity.getPeriod());
        schedule.setName(entity.getName());
        schedule.setStatus(ScheduleStatus.valueOf(entity.getStatus()));
        schedule.setPublishedAt(entity.getPublishedAt());
        schedule.setPublishedBy(entity.getPublishedBy());
        schedule.setRowVersion(entity.getRowVersion());
        schedule.setCreatedBy(entity.getCreatedBy());
        schedule.setCreatedAt(entity.getCreatedAt());
        schedule.setUpdatedAt(entity.getUpdatedAt());
        schedule.setActive(entity.isActive());
        schedule.setDeletedAt(entity.getDeletedAt());
        schedule.setDeletedBy(entity.getDeletedBy());
        return schedule;
    }
}
