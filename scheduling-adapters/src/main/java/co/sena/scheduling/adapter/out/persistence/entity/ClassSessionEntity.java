package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class_session", schema = "scheduling")
public class ClassSessionEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ScheduleEntity schedule;

    @Column(name = "competency_id", nullable = false)
    private UUID competencyId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "time_slot_id", nullable = false)
    private UUID timeSlotId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "day_of_week", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int dayOfWeek;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ScheduleEntity getSchedule() { return schedule; }
    public void setSchedule(ScheduleEntity schedule) { this.schedule = schedule; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() { return isActive; }
    public void setIsActive(boolean active) { isActive = active; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }
}
