package co.sena.scheduling.application.port.in;

import java.util.UUID;

/** PUT /sessions/{id} (contrato SessionUpdate: todo opcional). */
public class UpdateSessionCommand {

    private final UUID instructorId;
    private final UUID environmentId;
    private final UUID timeSlotId;
    private final String notes;
    private final boolean notesPresent;

    public UpdateSessionCommand(UUID instructorId, UUID environmentId, UUID timeSlotId,
                                String notes, boolean notesPresent) {
        this.instructorId = instructorId;
        this.environmentId = environmentId;
        this.timeSlotId = timeSlotId;
        this.notes = notes;
        this.notesPresent = notesPresent;
    }

    public UUID getInstructorId() { return instructorId; }
    public UUID getEnvironmentId() { return environmentId; }
    public UUID getTimeSlotId() { return timeSlotId; }
    public String getNotes() { return notes; }
    public boolean isNotesPresent() { return notesPresent; }
}
