package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "environment_availability_projection", schema = "scheduling")
public class EnvironmentAvailabilityProjectionEntity {

    @Id
    private UUID id;

    @Column(name = "environment_id", nullable = false, unique = true)
    private UUID environmentId;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    @Column
    private String reason;

    @Column(name = "last_event_id", nullable = false, unique = true)
    private UUID lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getLastEventId() { return lastEventId; }
    public void setLastEventId(UUID lastEventId) { this.lastEventId = lastEventId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
