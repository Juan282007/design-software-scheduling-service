package co.sena.scheduling.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Conflicto detectado durante la validación del horario.
 * Tipos: INSTRUCTOR_DOUBLE_BOOKED, ENVIRONMENT_DOUBLE_BOOKED,
 *        SESSIONS_OVERLAP, ENVIRONMENT_MAINTENANCE,
 *        INSTRUCTOR_UNAVAILABLE, INSTRUCTOR_TRAVEL_CONFLICT
 */
public class SchedulingConflict {

    private UUID id;
    private UUID scheduleId;
    private UUID sessionAId;
    private UUID sessionBId;
    private ConflictType conflictType;
    private String description;
    private boolean resolved;
    private Instant detectedAt;
    private ConflictSeverity severity;
    private UUID resolvedBy;
    private Instant resolvedAt;
    private String resolutionReason;
    private boolean blockingPublication;

    public SchedulingConflict() {}

    public SchedulingConflict(UUID scheduleId, UUID sessionAId, UUID sessionBId,
                              ConflictType conflictType, String description) {
        this.scheduleId = scheduleId;
        this.sessionAId = sessionAId;
        this.sessionBId = sessionBId;
        this.conflictType = conflictType;
        this.description = description;
        this.resolved = false;
        this.detectedAt = Instant.now();
        this.severity = ConflictSeverity.MEDIUM;
        this.blockingPublication = true;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public UUID getSessionAId() { return sessionAId; }
    public void setSessionAId(UUID sessionAId) { this.sessionAId = sessionAId; }

    public UUID getSessionBId() { return sessionBId; }
    public void setSessionBId(UUID sessionBId) { this.sessionBId = sessionBId; }

    public ConflictType getConflictType() { return conflictType; }
    public void setConflictType(ConflictType conflictType) { this.conflictType = conflictType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public ConflictSeverity getSeverity() { return severity; }
    public void setSeverity(ConflictSeverity severity) { this.severity = severity; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }

    public boolean isBlockingPublication() { return blockingPublication; }
    public void setBlockingPublication(boolean blockingPublication) { this.blockingPublication = blockingPublication; }

    /**
     * Marca el conflicto como resuelto.
     */
    public void resolve(UUID resolvedBy, String reason) {
        this.resolved = true;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
        this.resolutionReason = reason;
        this.blockingPublication = false;
    }
}


