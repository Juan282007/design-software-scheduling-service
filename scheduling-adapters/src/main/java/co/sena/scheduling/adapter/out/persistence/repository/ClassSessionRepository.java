package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.ClassSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSessionEntity, UUID>,
    JpaSpecificationExecutor<ClassSessionEntity> {

    List<ClassSessionEntity> findByScheduleId(UUID scheduleId);

    boolean existsByTimeSlotIdAndStatusAndIsActiveTrue(UUID timeSlotId, String status);

    Optional<ClassSessionEntity> findByIdAndIsActiveTrue(UUID id);

    /** Horarios vinculados al instructor (sesiones no eliminadas) para scope OWN_SCHEDULE. */
    @Query("""
        SELECT DISTINCT s.schedule.id FROM ClassSessionEntity s
        WHERE s.instructorId = :instructorId AND s.isActive = true
        """)
    List<UUID> findActiveScheduleIdsByInstructorId(@Param("instructorId") UUID instructorId);
}
