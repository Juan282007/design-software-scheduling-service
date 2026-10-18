package co.sena.scheduling.application.port.out;

import co.sena.scheduling.application.port.in.TimeSlotFilter;
import co.sena.scheduling.application.port.in.TimeSlotPage;
import co.sena.scheduling.domain.model.TimeSlot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia del catálogo de franjas horarias. */
public interface TimeSlotPersistencePort {

    TimeSlot save(TimeSlot timeSlot);

    Optional<TimeSlot> findActiveById(UUID id);

    TimeSlotPage search(TimeSlotFilter filter);

    boolean existsActiveClassSession(UUID timeSlotId);

    /**
     * Desactiva (soft-delete) la franja solo si ninguna sesión ACTIVE la referencia.
     * Atomicidad: la comprobación de referencia y la desactivación ocurren en una
     * única operación de escritura (sin TOCTOU entre check y update).
     *
     * @return filas afectadas: 1 = desactivada, 0 = no existe (activa) o está referenciada
     */
    int deactivateIfUnused(UUID id, Instant deletedAt, UUID deletedBy);
}
