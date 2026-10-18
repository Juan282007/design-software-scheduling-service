package co.sena.scheduling.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-SCH-004: Read model local de ambiente (ADR-002).
 * Proyección poblada por environment.availability.changed / maintenance.started.
 */
public class EnvironmentAvailabilityProjection {

    private UUID id;
    private UUID environmentId;
    private boolean available;
    private Instant effectiveFrom;
    private String reason;
    private UUID lastEventId;
    private Instant updatedAt;

    public EnvironmentAvailabilityProjection() {}

    public EnvironmentAvailabilityProjection(UUID environmentId, boolean available, UUID lastEventId) {
        this.id = UUID.randomUUID();
        this.environmentId = environmentId;
        this.available = available;
        this.lastEventId = lastEventId;
        this.updatedAt = Instant.now();
    }

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
