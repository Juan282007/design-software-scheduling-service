package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.EnvironmentAvailabilityProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.entity.InstructorAvailabilityProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.repository.EnvironmentAvailabilityProjectionRepository;
import co.sena.scheduling.adapter.out.persistence.repository.InstructorAvailabilityProjectionRepository;
import co.sena.scheduling.application.port.out.AvailabilityProjectionPort;
import co.sena.scheduling.domain.model.EnvironmentAvailabilityProjection;
import co.sena.scheduling.domain.model.InstructorAvailabilityProjection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class AvailabilityProjectionAdapter implements AvailabilityProjectionPort {

    private final EnvironmentAvailabilityProjectionRepository envRepo;
    private final InstructorAvailabilityProjectionRepository instructorRepo;

    public AvailabilityProjectionAdapter(EnvironmentAvailabilityProjectionRepository envRepo,
                                         InstructorAvailabilityProjectionRepository instructorRepo) {
        this.envRepo = envRepo;
        this.instructorRepo = instructorRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnvironmentAvailabilityProjection> findEnvironmentByEnvironmentId(UUID environmentId) {
        return envRepo.findByEnvironmentId(environmentId).map(this::toDomainEnv);
    }

    @Override
    @Transactional
    public EnvironmentAvailabilityProjection saveEnvironment(EnvironmentAvailabilityProjection projection) {
        EnvironmentAvailabilityProjectionEntity entity = toEntityEnv(projection);
        EnvironmentAvailabilityProjectionEntity saved = envRepo.save(entity);
        return toDomainEnv(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLastEventId(UUID eventId) {
        return envRepo.existsByLastEventId(eventId) || instructorRepo.existsByLastEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstructorAvailabilityProjection> findInstructorByInstructorIdAndCompetencyId(UUID instructorId, UUID competencyId) {
        return instructorRepo.findByInstructorIdAndCompetencyId(instructorId, competencyId).map(this::toDomainInstructor);
    }

    @Override
    @Transactional
    public InstructorAvailabilityProjection saveInstructor(InstructorAvailabilityProjection projection) {
        InstructorAvailabilityProjectionEntity entity = toEntityInstructor(projection);
        InstructorAvailabilityProjectionEntity saved = instructorRepo.save(entity);
        return toDomainInstructor(saved);
    }

    private EnvironmentAvailabilityProjectionEntity toEntityEnv(EnvironmentAvailabilityProjection d) {
        EnvironmentAvailabilityProjectionEntity e = new EnvironmentAvailabilityProjectionEntity();
        e.setId(d.getId());
        e.setEnvironmentId(d.getEnvironmentId());
        e.setAvailable(d.isAvailable());
        e.setEffectiveFrom(d.getEffectiveFrom());
        e.setReason(d.getReason());
        e.setLastEventId(d.getLastEventId());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    private EnvironmentAvailabilityProjection toDomainEnv(EnvironmentAvailabilityProjectionEntity e) {
        EnvironmentAvailabilityProjection d = new EnvironmentAvailabilityProjection();
        d.setId(e.getId());
        d.setEnvironmentId(e.getEnvironmentId());
        d.setAvailable(e.isAvailable());
        d.setEffectiveFrom(e.getEffectiveFrom());
        d.setReason(e.getReason());
        d.setLastEventId(e.getLastEventId());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }

    private InstructorAvailabilityProjectionEntity toEntityInstructor(InstructorAvailabilityProjection d) {
        InstructorAvailabilityProjectionEntity e = new InstructorAvailabilityProjectionEntity();
        e.setId(d.getId());
        e.setInstructorId(d.getInstructorId());
        e.setCompetencyId(d.getCompetencyId());
        e.setAvailable(d.getAvailable());
        e.setLastEventId(d.getLastEventId());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }

    private InstructorAvailabilityProjection toDomainInstructor(InstructorAvailabilityProjectionEntity e) {
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
