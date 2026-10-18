package co.sena.scheduling.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** POST /schedules/{id}/sessions (contrato SessionCreate). */
public class CreateSessionCommand {

    private final UUID scheduleId;
    private final UUID competencyId;
    private final UUID environmentId;
    private final UUID instructorId;
    private final UUID timeSlotId;
    private final LocalDate sessionDate;
    private final String notes;

    public CreateSessionCommand(UUID scheduleId, UUID competencyId, UUID environmentId,
                                UUID instructorId, UUID timeSlotId, LocalDate sessionDate,
                                String notes) {
        this.scheduleId = scheduleId;
        this.competencyId = competencyId;
        this.environmentId = environmentId;
        this.instructorId = instructorId;
        this.timeSlotId = timeSlotId;
        this.sessionDate = sessionDate;
        this.notes = notes;
    }

    public UUID getScheduleId() { return scheduleId; }
    public UUID getCompetencyId() { return competencyId; }
    public UUID getEnvironmentId() { return environmentId; }
    public UUID getInstructorId() { return instructorId; }
    public UUID getTimeSlotId() { return timeSlotId; }
    public LocalDate getSessionDate() { return sessionDate; }
    public String getNotes() { return notes; }
}
