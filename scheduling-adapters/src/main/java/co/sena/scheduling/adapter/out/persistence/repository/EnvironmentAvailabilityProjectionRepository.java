package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.EnvironmentAvailabilityProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentAvailabilityProjectionRepository extends JpaRepository<EnvironmentAvailabilityProjectionEntity, UUID> {
    Optional<EnvironmentAvailabilityProjectionEntity> findByEnvironmentId(UUID environmentId);
    boolean existsByLastEventId(UUID lastEventId);

    /** HU-SCH-005 E2: ambiente no disponible cuya efectividad ya aplica antes del instante dado. */
    @Query("""
        SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
        FROM EnvironmentAvailabilityProjectionEntity e
        WHERE e.environmentId = :environmentId
          AND e.available = false
          AND (e.effectiveFrom IS NULL OR e.effectiveFrom < :beforeInstant)
        """)
    boolean existsUnavailableBefore(@Param("environmentId") UUID environmentId,
                                    @Param("beforeInstant") Instant beforeInstant);
}
