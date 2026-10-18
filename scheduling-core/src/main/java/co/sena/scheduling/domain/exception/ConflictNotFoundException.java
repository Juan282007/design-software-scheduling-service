package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** El conflicto no existe (404) — HU-SCH-006 E1. */
public class ConflictNotFoundException extends RuntimeException {

    public ConflictNotFoundException(UUID id) {
        super("El conflicto solicitado no existe: " + id);
    }
}
