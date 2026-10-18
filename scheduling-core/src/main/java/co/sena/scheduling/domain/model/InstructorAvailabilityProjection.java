package co.sena.scheduling.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-SCH-004: Read model local de instructor/competencia (ADR-002).
 */
public class InstructorAvailabilityProjection {

    private UUID id;
    private UUID instructorId;
    private UUID competencyId;
    private boolean available;
    private UUID lastEventId;
    private Instant updatedAt;

    public InstructorAvailabilityProjection() {}

    public InstructorAvailabilityProjection(UUID instructorId, UUID competencyId, UUID lastEventId) {
        this.id = UUID.randomUUID();
        this.instructorId = instructorId;
        this.competencyId = competencyId;
        this.lastEventId = lastEventId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInstructorId() { return instructorId; }
    public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }
    public UUID getCompetencyId() { return competencyId; }
    public void setCompetencyId(UUID competencyId) { this.competencyId = competencyId; }
    public boolean getAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public UUID getLastEventId() { return lastEventId; }
    public void setLastEventId(UUID lastEventId) { this.lastEventId = lastEventId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
