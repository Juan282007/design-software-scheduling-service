package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ficha_training_center_projection", schema = "scheduling")
public class FichaTrainingCenterProjectionEntity {

    @Id
    @Column(name = "ficha_id", nullable = false)
    private UUID fichaId;

    @Column(name = "training_center_id", nullable = false)
    private UUID trainingCenterId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getFichaId() { return fichaId; }
    public void setFichaId(UUID fichaId) { this.fichaId = fichaId; }

    public UUID getTrainingCenterId() { return trainingCenterId; }
    public void setTrainingCenterId(UUID trainingCenterId) { this.trainingCenterId = trainingCenterId; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
