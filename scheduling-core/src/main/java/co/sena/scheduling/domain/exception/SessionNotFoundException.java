package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** La sesión no existe o fue eliminada (404). */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(UUID id) {
        super("La sesión de clase solicitada no existe: " + id);
    }
}
