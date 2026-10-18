package co.sena.scheduling.adapter.out.persistence.repository;

import co.sena.scheduling.adapter.out.persistence.entity.FichaTrainingCenterProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FichaTrainingCenterProjectionRepository
    extends JpaRepository<FichaTrainingCenterProjectionEntity, UUID> {

    boolean existsByFichaIdAndTrainingCenterId(UUID fichaId, UUID trainingCenterId);
}
