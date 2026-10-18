package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instructor_availability_projection", schema = "scheduling")
public class InstructorAvailabilityProjectionEntity {

    @Id
    private UUID id;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "competency_id")
    private UUID competencyId;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "last_event_id", nullable = false, unique = true)
    private UUID lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
