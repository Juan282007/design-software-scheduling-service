package co.sena.scheduling.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Sesión de clase: instancia de una clase vinculando
 * ficha → instructor → ambiente → franja.
 *
 * Desnormalización intencional: start_time, end_time, day_of_week
 * son copias inmutables de time_slot para detección O(1) de conflictos.
 */
public class ClassSession {

    private UUID id;
    private UUID scheduleId;
    private UUID competencyId;
    private UUID environmentId;
    private UUID instructorId;
    private UUID timeSlotId;
    private LocalTime startTime;
    private LocalTime endTime;
    private int dayOfWeek;
    private LocalDate sessionDate;
    private SessionStatus status;
    private String notes;
    private Instant updatedAt;
    private boolean active = true;
    private Instant deletedAt;
    private UUID deletedBy;

    public ClassSession() {}

    public ClassSession(UUID scheduleId, UUID competencyId, UUID environmentId,
                        UUID instructorId, UUID timeSlotId, LocalDate sessionDate,
                        LocalTime startTime, LocalTime endTime, int dayOfWeek) {
        this.scheduleId = scheduleId;
        this.competencyId = competencyId;
        this.environmentId = environmentId;
        this.instructorId = instructorId;
        this.timeSlotId = timeSlotId;
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dayOfWeek = dayOfWeek;
        this.status = SessionStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getScheduleId() { return scheduleId; }
    public void setScheduleId(UUID scheduleId) { this.scheduleId = scheduleId; }

    public UUID getCompetencyId() { return competencyId; }
    public void setCompetencyId(UUID competencyId) { this.competencyId = competencyId; }

    public UUID getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(UUID environmentId) { this.environmentId = environmentId; }

    public UUID getInstructorId() { return instructorId; }
    public void setInstructorId(UUID instructorId) { this.instructorId = instructorId; }

    public UUID getTimeSlotId() { return timeSlotId; }
    public void setTimeSlotId(UUID timeSlotId) { this.timeSlotId = timeSlotId; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate sessionDate) { this.sessionDate = sessionDate; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }

    public void deactivate(Instant at, UUID actor) {
        this.active = false;
        this.deletedAt = at;
        this.deletedBy = actor;
    }

    /**
     * Cancela la sesión (ACTIVE → CANCELLED).
     */
    public void cancel() {
        if (this.status != SessionStatus.ACTIVE) {
            throw new IllegalStateException(
                "Solo se puede cancelar una sesión ACTIVE. Estado actual: " + this.status);
        }
        this.status = SessionStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
