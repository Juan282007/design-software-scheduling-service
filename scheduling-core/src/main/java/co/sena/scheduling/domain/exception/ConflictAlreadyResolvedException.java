package co.sena.scheduling.domain.exception;

import java.util.UUID;

/** El conflicto ya estaba resuelto (409) — HU-SCH-006 E1. */
public class ConflictAlreadyResolvedException extends RuntimeException {

    public ConflictAlreadyResolvedException(UUID id) {
        super("El conflicto ya está resuelto: " + id);
    }
}
