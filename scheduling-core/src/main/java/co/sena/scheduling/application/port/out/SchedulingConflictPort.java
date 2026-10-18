package co.sena.scheduling.application.port.out;

import co.sena.scheduling.domain.model.SchedulingConflict;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SchedulingConflictPort {
    List<SchedulingConflict> saveAll(List<SchedulingConflict> conflicts);
    List<SchedulingConflict> findByScheduleId(UUID scheduleId);
    List<SchedulingConflict> findByScheduleIdAndBlocking(UUID scheduleId, boolean blocking);

    /** HU-SCH-006 E1: conflicto por id (vacío si no existe → 404). */
    java.util.Optional<SchedulingConflict> findById(UUID conflictId);

    /**
     * HU-SCH-006 E1: resolución condicional atómica (una sola sentencia
     * {@code UPDATE ... WHERE is_resolved = false}). A diferencia de un
     * check-then-act, el ganador lo decide la BD: ante dos resoluciones
     * concurrentes exactamente una devuelve {@code true} (la otra vería un
     * conflicto ya resuelto → 409, que es el caso de carrera que define la UX).
     *
     * @return {@code true} solo si esta llamada resolvió el conflicto;
     *         {@code false} si no existía o ya estaba resuelto.
     */
    boolean resolveIfPending(UUID conflictId, UUID resolvedBy,
                             String resolutionReason, java.time.Instant resolvedAt);

    /**
     * HU-SCH-005 E3: reconcilia los conflictos detectados de forma idempotente,
     * acotado a las sesiones de {@code scopedSessionIds} (evita borrar conflictos
     * de sesiones fuera del alcance de la corrida).
     *
     * <ul>
     *   <li>Clave: tipo + par de sesiones en orden canónico.</li>
     *   <li>Si la clave ya existe no se inserta de nuevo (sin duplicados y sin
     *       reabrir conflictos ya resueltos por un humano).</li>
     *   <li>Los conflictos no resueltos del alcance que ya no se detectan se
     *       eliminan (la condición desapareció).</li>
     *   <li>Los resueltos se conservan siempre (histórico de resolución).</li>
     * </ul>
     *
     * @return los conflictos vigentes del alcance después de reconciliar.
     */
    List<SchedulingConflict> reconcileDetected(UUID scheduleId, Set<UUID> scopedSessionIds,
                                               List<SchedulingConflict> detected);
}
