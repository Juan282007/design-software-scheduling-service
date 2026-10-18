package co.sena.scheduling.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto para consultar tiempos de traslado entre sedes (D-11, tabla
 * scheduling.site_travel_time). Usada por HU-SCH-005 E2 para detectar
 * INSTRUCTOR_TRAVEL_CONFLICT.
 *
 * <p>Asunción explícita (HU-SCH-005): scheduling-service no dispone de un
 * read model ambiente→sede, así que la detección usa el {@code environment_id}
 * de la sesión como referencia lógica de sitio al consultar la matriz.
 * Cuando exista ese read model basta con resolverlo antes de llamar este puerto.</p>
 */
public interface SiteTravelTimePort {

    /**
     * Obtiene el tiempo de traslado en minutos de origen a destino.
     * Retorna vacío si no hay registro activo entre esos sitios (la detección
     * no emite conflicto cuando no hay dato de traslado).
     */
    Optional<Integer> getTravelTimeMinutes(UUID originSiteId, UUID destinationSiteId);
}
