package co.sena.scheduling.application.port.in;

import java.util.UUID;

/**
 * Contexto de acceso resuelto del JWT para E2 (HU-002 + E-scope HU-008 #154).
 * GLOBAL/TRAINING_CENTER: scopes administrativos aceptados por contrato.
 * OWNER_INSTRUCTOR: solo horarios con sesiones del instructor.
 * OWNER_FICHA: solo horarios de la ficha del aprendiz autenticado.
 * DENIED: fail-closed para scopes no resolvibles. Auth no-JWT (tests/scheduler):
 * UNRESTRICTED legacy.
 */
public class ScheduleAccess {

    public enum Type {
        GLOBAL,
        TRAINING_CENTER,
        OWNER_INSTRUCTOR,
        OWNER_FICHA,
        DENIED,
        UNRESTRICTED
    }

    private final Type type;
    private final UUID trainingCenterId;
    private final UUID instructorId;
    private final UUID fichaId;
    private final String reason;

    private ScheduleAccess(Type type, UUID trainingCenterId, UUID instructorId, UUID fichaId, String reason) {
        this.type = type;
        this.trainingCenterId = trainingCenterId;
        this.instructorId = instructorId;
        this.fichaId = fichaId;
        this.reason = reason;
    }

    public static ScheduleAccess global() {
        return new ScheduleAccess(Type.GLOBAL, null, null, null, null);
    }

    public static ScheduleAccess trainingCenter(UUID trainingCenterId) {
        return new ScheduleAccess(Type.TRAINING_CENTER, trainingCenterId, null, null, null);
    }

    public static ScheduleAccess ownerInstructor(UUID instructorId) {
        return new ScheduleAccess(Type.OWNER_INSTRUCTOR, null, instructorId, null, null);
    }

    public static ScheduleAccess ownerFicha(UUID fichaId) {
        return new ScheduleAccess(Type.OWNER_FICHA, null, null, fichaId, null);
    }

    public static ScheduleAccess denied(String reason) {
        return new ScheduleAccess(Type.DENIED, null, null, null, reason);
    }

    public static ScheduleAccess unrestricted() {
        return new ScheduleAccess(Type.UNRESTRICTED, null, null, null, null);
    }

    public Type getType() { return type; }
    public UUID getTrainingCenterId() { return trainingCenterId; }
    public UUID getInstructorId() { return instructorId; }
    public UUID getFichaId() { return fichaId; }
    public String getReason() { return reason; }
}
