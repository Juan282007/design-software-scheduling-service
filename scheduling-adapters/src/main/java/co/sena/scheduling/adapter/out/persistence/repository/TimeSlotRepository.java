package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.TimeSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlotEntity, UUID> {

    Optional<TimeSlotEntity> findByIdAndIsActiveTrue(UUID id);

    @Query("""
        SELECT t FROM TimeSlotEntity t
        WHERE t.isActive = true
          AND (:dayOfWeek IS NULL OR t.dayOfWeek = :dayOfWeek)
          AND (:shift IS NULL OR t.shift = :shift)
        """)
    Page<TimeSlotEntity> searchActive(
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("shift") String shift,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE TimeSlotEntity t
        SET t.isActive = false, t.deletedAt = :deletedAt, t.deletedBy = :deletedBy
        WHERE t.id = :id AND t.isActive = true
          AND NOT EXISTS (
              SELECT 1 FROM ClassSessionEntity s
              WHERE s.timeSlotId = :id AND s.status = 'ACTIVE' AND s.isActive = true
          )
        """)
    int deactivateIfUnused(@Param("id") UUID id,
                           @Param("deletedAt") Instant deletedAt,
                           @Param("deletedBy") UUID deletedBy);
}
