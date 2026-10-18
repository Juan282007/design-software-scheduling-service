package co.sena.scheduling.adapter.out.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "time_slot", schema = "scheduling")
public class TimeSlotEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String name;

    // DDL Liquibase: day_of_week es smallint; sin este mapeo, ddl-auto=validate
    // revienta el arranque real (SMALLINT vs INTEGER) aunque los ITs con ddl=none no lo vean.
    @Column(name = "day_of_week", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 20)
    private String shift;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public boolean isActive() { return isActive; }
    public void setIsActive(boolean active) { isActive = active; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }
}
