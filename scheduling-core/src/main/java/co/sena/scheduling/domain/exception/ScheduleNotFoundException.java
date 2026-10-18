package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** El horario no existe o fue eliminado (404). */
public class ScheduleNotFoundException extends RuntimeException {

    public ScheduleNotFoundException(UUID id) {
        super("El horario solicitado no existe: " + id);
    }
}
