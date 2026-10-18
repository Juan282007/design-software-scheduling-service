package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** Indica que la franja no existe o ya fue eliminada lógicamente. */
public class TimeSlotNotFoundException extends RuntimeException {

    public TimeSlotNotFoundException(UUID id) {
        super("No existe una franja horaria activa con id: " + id);
    }
}
