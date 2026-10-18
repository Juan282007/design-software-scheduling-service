package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduling_conflict", schema = "scheduling")
public class SchedulingConflictEntity {

    @Id
    private UUID id;

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "session_a_id", nullable = false)
    private UUID sessionAId;

    @Column(name = "session_b_id")
    private UUID sessionBId;

    @Column(name = "conflict_type", nullable = false, length = 50)
    private String conflictType;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_resolved", nullable = false)
    private boolean resolved;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_reason")
    private String resolutionReason;

    @Column(name = "is_blocking_publication", nullable = false)
    private boolean blockingPublication;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public UUID getSessionAId() { return sessionAId; }
    public void setSessionAId(UUID sessionAId) { this.sessionAId = sessionAId; }

    public UUID getSessionBId() { return sessionBId; }
    public void setSessionBId(UUID sessionBId) { this.sessionBId = sessionBId; }

    public String getConflictType() { return conflictType; }
    public void setConflictType(String conflictType) { this.conflictType = conflictType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }

    public boolean isBlockingPublication() { return blockingPublication; }
    public void setBlockingPublication(boolean blockingPublication) { this.blockingPublication = blockingPublication; }
}
