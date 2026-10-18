package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.SchedulingConflictEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SchedulingConflictRepository extends JpaRepository<SchedulingConflictEntity, UUID> {

    List<SchedulingConflictEntity> findByScheduleIdAndResolvedFalse(UUID scheduleId);

    List<SchedulingConflictEntity> findByScheduleId(UUID scheduleId);

    List<SchedulingConflictEntity> findByScheduleIdAndBlockingPublication(UUID scheduleId, boolean blocking);

    /**
     * HU-SCH-006 E1: resolución condicional atómica (update-and-check en una
     * sola sentencia). La cláusula {@code WHERE is_resolved = false} decide el
     * ganador dentro de la BD, por lo que dos resoluciones concurrentes no
     * pueden devolver ambas 200 (corrige la carrera check-then-act reportada
     * por QA en PR #12). Espejo campo a campo de
     * {@link co.sena.scheduling.domain.model.SchedulingConflict#resolve}.
     *
     * @return 1 si esta sentencia resolvió el conflicto; 0 si no existía o ya
     *         estaba resuelto.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE SchedulingConflictEntity c
        SET c.resolved = true,
            c.resolvedBy = :resolvedBy,
            c.resolvedAt = :resolvedAt,
            c.resolutionReason = :resolutionReason,
            c.blockingPublication = false
        WHERE c.id = :id AND c.resolved = false
        """)
    int resolveIfPending(@Param("id") UUID id,
                         @Param("resolvedBy") UUID resolvedBy,
                         @Param("resolvedAt") Instant resolvedAt,
                         @Param("resolutionReason") String resolutionReason);
}
