package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<ScheduleEntity, UUID>,
    JpaSpecificationExecutor<ScheduleEntity> {

    Optional<ScheduleEntity> findById(UUID id);

    List<ScheduleEntity> findByFichaId(UUID fichaId);

    List<ScheduleEntity> findByFichaIdAndPeriod(UUID fichaId, String period);

    @Query("SELECT s FROM ScheduleEntity s WHERE s.fichaId = :fichaId AND s.period = :period AND s.status = 'PUBLISHED'")
    Optional<ScheduleEntity> findPublishedByFichaIdAndPeriod(
            @Param("fichaId") UUID fichaId,
            @Param("period") String period);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE ScheduleEntity s
        SET s.status = 'ARCHIVED'
        WHERE s.fichaId = :fichaId
          AND s.period = :period
          AND s.status = 'PUBLISHED'
          AND s.id <> :scheduleId
        """)
    int archivePublishedByFichaIdAndPeriodExcept(
            @Param("fichaId") UUID fichaId,
            @Param("period") String period,
            @Param("scheduleId") UUID scheduleId);

    boolean existsByFichaIdAndPeriodAndStatus(UUID fichaId, String period, String status);

    Optional<ScheduleEntity> findByIdAndIsActiveTrue(UUID id);
}
