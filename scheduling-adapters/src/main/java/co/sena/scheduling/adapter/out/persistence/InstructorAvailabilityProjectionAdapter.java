package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.InstructorAvailabilityProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.repository.InstructorAvailabilityProjectionRepository;
import co.sena.scheduling.application.port.out.InstructorAvailabilityProjectionPort;
import co.sena.scheduling.domain.model.InstructorAvailabilityProjection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * HU-SCH-005 E2: consulta del read model de instructores (HU-004) para
 * detectar INSTRUCTOR_UNAVAILABLE.
 */
@Component
public class InstructorAvailabilityProjectionAdapter implements InstructorAvailabilityProjectionPort {

    private final InstructorAvailabilityProjectionRepository repository;

    public InstructorAvailabilityProjectionAdapter(InstructorAvailabilityProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstructorAvailabilityProjection> findByInstructorIdAndCompetencyId(UUID instructorId, UUID competencyId) {
        return repository.findByInstructorIdAndCompetencyId(instructorId, competencyId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInstructorUnavailable(UUID instructorId, UUID competencyId) {
        if (instructorId == null) {
            return false;
        }
        return repository.existsUnavailableForCompetency(instructorId, competencyId);
    }

    private InstructorAvailabilityProjection toDomain(InstructorAvailabilityProjectionEntity e) {
        InstructorAvailabilityProjection d = new InstructorAvailabilityProjection();
        d.setId(e.getId());
        d.setInstructorId(e.getInstructorId());
        d.setCompetencyId(e.getCompetencyId());
        d.setAvailable(e.getAvailable());
        d.setLastEventId(e.getLastEventId());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }
}
