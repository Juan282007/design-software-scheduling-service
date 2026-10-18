package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.ClassSessionEntity;
import co.sena.scheduling.adapter.out.persistence.entity.FichaTrainingCenterProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.repository.ClassSessionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.ScheduleRepository;
import co.sena.scheduling.application.port.in.SessionFilter;
import co.sena.scheduling.application.port.in.SessionPage;
import co.sena.scheduling.application.port.out.SessionPersistencePort;
import co.sena.scheduling.domain.exception.SessionValidationException;
import co.sena.scheduling.domain.model.ClassSession;
import co.sena.scheduling.domain.model.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA de sesiones de clase (HU-003). E6: no mapea execution_status. */
@Component
public class SessionPersistenceAdapter implements SessionPersistencePort {

    private final ClassSessionRepository sessionRepository;
    private final ScheduleRepository scheduleRepository;

    public SessionPersistenceAdapter(ClassSessionRepository sessionRepository,
                                     ScheduleRepository scheduleRepository) {
        this.sessionRepository = sessionRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public ClassSession save(ClassSession session) {
        ClassSessionEntity saved = sessionRepository.save(toEntity(session));
        // Fuerza el flush para que el EXCLUDE se evalúe aquí (dentro del try
        // del caso de uso) y no en el commit, donde ya no se puede mapear a 409 fino.
        sessionRepository.flush();
        return toDomain(saved);
    }

    @Override
    public Optional<ClassSession> findActiveById(UUID id) {
        return sessionRepository.findByIdAndIsActiveTrue(id).map(this::toDomain);
    }

    @Override
    public List<ClassSession> findByScheduleId(UUID scheduleId) {
        return sessionRepository.findByScheduleId(scheduleId).stream().map(this::toDomain).toList();
    }

    @Override
    public SessionPage search(SessionFilter filter) {
        return search(filter, null);
    }

    @Override
    public SessionPage search(SessionFilter filter, UUID ownerInstructorId) {
        return search(filter, ownerInstructorId, null);
    }

    @Override
    public SessionPage search(SessionFilter filter, UUID ownerInstructorId, UUID ownerFichaId) {
        return search(filter, ownerInstructorId, ownerFichaId, null);
    }

    @Override
    public SessionPage search(SessionFilter filter, UUID ownerInstructorId, UUID ownerFichaId,
                              UUID trainingCenterId) {
        if (filter == null) {
            throw new SessionValidationException("payload", "REQUIRED", "El filtro es obligatorio");
        }
        if (filter.getPage() < 1) {
            throw new SessionValidationException("page", "OUT_OF_RANGE", "page debe ser mayor o igual a 1");
        }
        if (filter.getPageSize() < 1 || filter.getPageSize() > 100) {
            throw new SessionValidationException("page_size", "OUT_OF_RANGE", "page_size debe estar entre 1 y 100");
        }
        if (filter.getStatus() != null) {
            try {
                SessionStatus.valueOf(filter.getStatus());
            } catch (IllegalArgumentException ex) {
                throw new SessionValidationException("status", "INVALID_VALUE",
                    "status debe ser ACTIVE o CANCELLED");
            }
        }
        Specification<ClassSessionEntity> spec = Specification
            .where((root, query, cb) -> cb.isTrue(root.get("isActive")));
        if (filter.getScheduleId() != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("schedule").get("id"), filter.getScheduleId()));
        }
        if (ownerFichaId != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("schedule").get("fichaId"), ownerFichaId));
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("schedule").get("status"), "PUBLISHED"));
        }
        if (ownerInstructorId != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("schedule").get("status"), "PUBLISHED"));
        }
        if (trainingCenterId != null) {
            spec = spec.and((root, query, cb) -> {
                var subquery = query.subquery(UUID.class);
                var projection = subquery.from(FichaTrainingCenterProjectionEntity.class);
                subquery.select(projection.get("fichaId"))
                    .where(
                        cb.equal(projection.get("fichaId"), root.get("schedule").get("fichaId")),
                        cb.equal(projection.get("trainingCenterId"), trainingCenterId)
                    );
                return cb.exists(subquery);
            });
        }
        UUID instructor = ownerInstructorId != null ? ownerInstructorId : filter.getInstructorId();
        if (instructor != null) {
            final UUID instructorId = instructor;
            spec = spec.and((root, query, cb) -> cb.equal(root.get("instructorId"), instructorId));
        }
        if (filter.getEnvironmentId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("environmentId"), filter.getEnvironmentId()));
        }
        if (filter.getStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.getStatus()));
        }
        if (filter.getFrom() != null) {
            spec = spec.and((root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("sessionDate"), filter.getFrom()));
        }
        if (filter.getTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("sessionDate"), filter.getTo()));
        }
        PageRequest pageRequest = PageRequest.of(
            filter.getPage() - 1, filter.getPageSize(), resolveSort(filter.getSort()));
        Page<ClassSessionEntity> page = sessionRepository.findAll(spec, pageRequest);
        return new SessionPage(
            page.getContent().stream().map(this::toDomain).toList(),
            filter.getPage(), filter.getPageSize(), page.getTotalElements());
    }

    private Sort resolveSort(String sortExpression) {
        if (sortExpression == null || sortExpression.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "sessionDate");
        }
        String[] tokens = sortExpression.trim().split(":", 2);
        String field = switch (tokens[0]) {
            case "session_date" -> "sessionDate";
            case "updated_at" -> "updatedAt";
            case "status" -> "status";
            default -> throw new SessionValidationException(
                "sort", "UNSUPPORTED_FIELD", "El campo de ordenamiento no está permitido");
        };
        Sort.Direction direction = tokens.length == 2 && "desc".equalsIgnoreCase(tokens[1])
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (tokens.length == 2 && !"asc".equalsIgnoreCase(tokens[1]) && !"desc".equalsIgnoreCase(tokens[1])) {
            throw new SessionValidationException(
                "sort", "INVALID_DIRECTION", "La dirección debe ser asc o desc");
        }
        return Sort.by(direction, field);
    }

    private ClassSessionEntity toEntity(ClassSession session) {
        ClassSessionEntity entity = new ClassSessionEntity();
        entity.setId(session.getId());
        if (session.getScheduleId() != null) {
            entity.setSchedule(scheduleRepository.getReferenceById(session.getScheduleId()));
        }
        entity.setCompetencyId(session.getCompetencyId());
        entity.setEnvironmentId(session.getEnvironmentId());
        entity.setInstructorId(session.getInstructorId());
        entity.setTimeSlotId(session.getTimeSlotId());
        entity.setStartTime(session.getStartTime());
        entity.setEndTime(session.getEndTime());
        entity.setDayOfWeek(session.getDayOfWeek());
        entity.setSessionDate(session.getSessionDate());
        entity.setStatus(session.getStatus() == null ? null : session.getStatus().name());
        entity.setNotes(session.getNotes());
        entity.setUpdatedAt(session.getUpdatedAt());
        entity.setIsActive(session.isActive());
        entity.setDeletedAt(session.getDeletedAt());
        entity.setDeletedBy(session.getDeletedBy());
        return entity;
    }

    private ClassSession toDomain(ClassSessionEntity entity) {
        ClassSession session = new ClassSession();
        session.setId(entity.getId());
        if (entity.getSchedule() != null) {
            session.setScheduleId(entity.getSchedule().getId());
        }
        session.setCompetencyId(entity.getCompetencyId());
        session.setEnvironmentId(entity.getEnvironmentId());
        session.setInstructorId(entity.getInstructorId());
        session.setTimeSlotId(entity.getTimeSlotId());
        session.setStartTime(entity.getStartTime());
        session.setEndTime(entity.getEndTime());
        session.setDayOfWeek(entity.getDayOfWeek());
        session.setSessionDate(entity.getSessionDate());
        if (entity.getStatus() != null) {
            session.setStatus(SessionStatus.valueOf(entity.getStatus()));
        }
        session.setNotes(entity.getNotes());
        session.setUpdatedAt(entity.getUpdatedAt());
        session.setActive(entity.isActive());
        session.setDeletedAt(entity.getDeletedAt());
        session.setDeletedBy(entity.getDeletedBy());
        return session;
    }
}
