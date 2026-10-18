package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.persistence.entity.EnvironmentAvailabilityProjectionEntity;
import co.sena.scheduling.adapter.out.persistence.repository.EnvironmentAvailabilityProjectionRepository;
import co.sena.scheduling.application.port.out.EnvironmentAvailabilityProjectionPort;
import co.sena.scheduling.domain.model.EnvironmentAvailabilityProjection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-SCH-005 E2: consulta del read model de ambientes (HU-004) para
 * detectar ENVIRONMENT_MAINTENANCE.
 */
@Component
public class EnvironmentAvailabilityProjectionAdapter implements EnvironmentAvailabilityProjectionPort {

    private final EnvironmentAvailabilityProjectionRepository repository;

    public EnvironmentAvailabilityProjectionAdapter(EnvironmentAvailabilityProjectionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnvironmentAvailabilityProjection> findByEnvironmentId(UUID environmentId) {
        return repository.findByEnvironmentId(environmentId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnvironmentUnavailable(UUID environmentId, LocalDate date) {
        if (environmentId == null || date == null) {
            return false;
        }
        Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return repository.existsUnavailableBefore(environmentId, endOfDay);
    }

    private EnvironmentAvailabilityProjection toDomain(EnvironmentAvailabilityProjectionEntity e) {
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
}
