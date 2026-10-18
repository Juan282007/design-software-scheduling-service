package co.sena.scheduling.domain.service;

import co.sena.scheduling.application.port.out.AffectedSessionPort;
import co.sena.scheduling.application.port.out.AvailabilityProjectionPort;
import co.sena.scheduling.domain.model.EnvironmentAvailabilityProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HU-SCH-004: Servicio de dominio para read models (ADR-002).
 * E1: deduplicación por event_id. E2: maintenance marca no disponible, identifica sesiones y emite conflicto.
 */
public class AvailabilityProjectionService {

    private final AvailabilityProjectionPort port;
    private final AffectedSessionPort affectedSessionPort;

    public AvailabilityProjectionService(AvailabilityProjectionPort port, AffectedSessionPort affectedSessionPort) {
        this.port = port;
        this.affectedSessionPort = affectedSessionPort;
    }

    // Constructor for tests without AffectedSessionPort (backward compat, but not used in production)
    public AvailabilityProjectionService(AvailabilityProjectionPort port) {
        this(port, (envId, start, end) -> List.of());
    }

    /**
     * E1: Maneja environment.availability.changed de forma idempotente.
     * @return true si se aplicó, false si ya existía (deduplicado)
     */
    public boolean handleAvailabilityChanged(UUID eventId, UUID environmentId, boolean available, Instant effectiveFrom, String reason) {
        if (port.existsByLastEventId(eventId)) {
            return false;
        }
        EnvironmentAvailabilityProjection projection = port.findEnvironmentByEnvironmentId(environmentId)
                .orElse(new EnvironmentAvailabilityProjection(environmentId, available, eventId));
        projection.setAvailable(available);
        projection.setEffectiveFrom(effectiveFrom);
        projection.setReason(reason);
        projection.setLastEventId(eventId);
        projection.setUpdatedAt(Instant.now());
        port.saveEnvironment(projection);
        return true;
    }

    /**
     * E2: Maneja environment.maintenance.started.
     * Marca el ambiente como no disponible y identifica sesiones futuras ACTIVE afectadas.
     * No cancela sesiones automáticamente (HU-SCH-003).
     * @return lista de sessionIds afectadas (para que el caller emita scheduling.conflict.detected)
     */
    public List<UUID> handleMaintenanceStarted(UUID eventId, UUID environmentId, Instant startDate, Instant endDate, String description) {
        if (port.existsByLastEventId(eventId)) {
            return List.of();
        }
        EnvironmentAvailabilityProjection projection = port.findEnvironmentByEnvironmentId(environmentId)
                .orElse(new EnvironmentAvailabilityProjection(environmentId, false, eventId));
        projection.setAvailable(false);
        projection.setEffectiveFrom(startDate);
        projection.setReason("maintenance: " + description + " [" + startDate + " - " + endDate + "]");
        projection.setLastEventId(eventId);
        projection.setUpdatedAt(Instant.now());
        port.saveEnvironment(projection);

        // E2: Identificar sesiones futuras ACTIVE afectadas (producción, no solo test)
        LocalDate startLocal = startDate.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate endLocal = endDate.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        List<UUID> affected = affectedSessionPort.findActiveSessionIdsByEnvironmentIdAndDateRange(environmentId, startLocal, endLocal);
        // El caller (consumer) debe emitir scheduling.conflict.detected con conflict_type ENVIRONMENT_MAINTENANCE por cada sesión
        return affected;
    }

    // Overload for consumer that expects boolean return (for backward compat)
    public boolean handleMaintenanceStartedBool(UUID eventId, UUID environmentId, Instant startDate, Instant endDate, String description) {
        if (port.existsByLastEventId(eventId)) {
            return false;
        }
        handleMaintenanceStarted(eventId, environmentId, startDate, endDate, description);
        return true;
    }
}
