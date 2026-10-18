package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.SchedulingConflict;

import java.util.UUID;

/**
 * Use case para marcar un conflicto como resuelto (HU-SCH-006 E1).
 */
public interface ResolveConflictUseCase {

    /**
     * Marca el conflicto como resuelto y persiste la resolución.
     *
     * @param conflictId id del conflicto (404 si no existe).
     * @param resolvedBy actor que resuelve (sub del JWT) → {@code resolved_by}.
     * @return el conflicto resuelto.
     * @throws co.sena.scheduling.domain.exception.ConflictNotFoundException si no existe.
     * @throws co.sena.scheduling.domain.exception.ConflictAlreadyResolvedException si ya estaba resuelto (409).
     */
    SchedulingConflict resolve(UUID conflictId, UUID resolvedBy);
}
