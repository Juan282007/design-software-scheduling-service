package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** Ya existe un PUBLISHED para (ficha_id, period) (409, red de seguridad: índice parcial). */
public class SchedulePublishedExistsException extends RuntimeException {

    public SchedulePublishedExistsException(UUID fichaId, String period) {
        super("Ya existe un horario PUBLISHED para ficha_id=" + fichaId + " period=" + period);
    }
}
