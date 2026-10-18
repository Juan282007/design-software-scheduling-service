package co.sena.scheduling.domain.model;

import java.time.LocalTime;
import java.time.Instant;
import java.util.UUID;

/**
 * Franja horaria reutilizable (catálogo).
 * Inmutable tras creación — las class_session copian sus campos.
 */
public class TimeSlot {

    private UUID id;
    private String name;
    private int dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftType shift;
    private boolean active = true;
    private Instant deletedAt;
    private UUID deletedBy;

    public TimeSlot() {}

    public TimeSlot(UUID id, String name, int dayOfWeek, LocalTime startTime,
                    LocalTime endTime, ShiftType shift) {
        this.id = id;
        this.name = name;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.shift = shift;
    }

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

    public ShiftType getShift() { return shift; }
    public void setShift(ShiftType shift) { this.shift = shift; }

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
}
