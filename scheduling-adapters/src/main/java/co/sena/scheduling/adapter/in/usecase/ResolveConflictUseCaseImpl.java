package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.ResolveConflictUseCase;
import co.sena.scheduling.application.port.out.SchedulingConflictPort;
import co.sena.scheduling.domain.exception.ConflictAlreadyResolvedException;
import co.sena.scheduling.domain.exception.ConflictNotFoundException;
import co.sena.scheduling.domain.model.SchedulingConflict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-SCH-006 — Resolución de conflictos.
 *
 * <ul>
 *   <li>E1: 404 si no existe, 409 si ya estaba resuelto. La decisión es una
 *       actualización condicional atómica ({@code UPDATE ... WHERE
 *       is_resolved = false}), no un check-then-act: ante dos peticiones
 *       concurrentes exactamente una recibe 200 y la otra 409 — el caso de
 *       carrera que la UX define como "already resolved by another user"
 *       (bloqueo de QA en PR #12).</li>
 *   <li>E2: persiste {@code resolved_by} (actor del JWT) y {@code resolved_at}
 *       (reloj del servidor, {@link Instant#now()}); el contrato no declara
 *       body, por lo que {@code resolution_reason} queda {@code null}
 *       (discrepancia de contrato documentada — no se inventa el campo).</li>
 *   <li>E3: la resolución pone {@code is_blocking_publication=false}; el
 *       registro se conserva para auditoría y la re-detección de HU-SCH-005 no
 *       lo reabre.</li>
 * </ul>
 */
@Component
public class ResolveConflictUseCaseImpl implements ResolveConflictUseCase {

    private final SchedulingConflictPort conflictPort;

    public ResolveConflictUseCaseImpl(SchedulingConflictPort conflictPort) {
        this.conflictPort = conflictPort;
    }

    @Override
    @Transactional
    public SchedulingConflict resolve(UUID conflictId, UUID resolvedBy) {
        boolean won = conflictPort.resolveIfPending(conflictId, resolvedBy, null, Instant.now());
        if (won) {
            return conflictPort.findById(conflictId)
                    .orElseThrow(() -> new ConflictNotFoundException(conflictId));
        }
        // La sentencia condicional no aplicó: o el conflicto no existe (404)
        // o otra transacción ya lo resolvió (409 — exactamente un ganador).
        if (conflictPort.findById(conflictId).isEmpty()) {
            throw new ConflictNotFoundException(conflictId);
        }
        throw new ConflictAlreadyResolvedException(conflictId);
    }
}
