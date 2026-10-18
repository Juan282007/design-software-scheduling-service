package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.InstructorAvailabilityProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface InstructorAvailabilityProjectionRepository extends JpaRepository<InstructorAvailabilityProjectionEntity, UUID> {
    Optional<InstructorAvailabilityProjectionEntity> findByInstructorIdAndCompetencyId(UUID instructorId, UUID competencyId);
    boolean existsByLastEventId(UUID lastEventId);

    /** HU-SCH-005 E2: instructor no disponible para la competencia (o sin competencia = regla general). */
    @Query("""
        SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
        FROM InstructorAvailabilityProjectionEntity i
        WHERE i.instructorId = :instructorId
          AND i.available = false
          AND (i.competencyId IS NULL OR i.competencyId = :competencyId)
        """)
    boolean existsUnavailableForCompetency(@Param("instructorId") UUID instructorId,
                                           @Param("competencyId") UUID competencyId);
}
