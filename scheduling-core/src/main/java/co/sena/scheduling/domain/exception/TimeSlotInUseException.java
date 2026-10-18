package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** La franja no puede eliminarse mientras tenga sesiones activas. */
public class TimeSlotInUseException extends RuntimeException {

    public TimeSlotInUseException(UUID id) {
        super("La franja horaria está referenciada por sesiones de clase activas: " + id);
    }
}
