package co.sena.scheduling.application.port.out;

import java.util.UUID;

/**
 * Staging mínimo de eventos en scheduling.outbox (HU-003 E5 local, arquitecto).
 * Persiste PENDING en la misma transacción; el relay/broker es HU-SCH-007.
 */
public interface OutboxStagingPort {

    void stage(UUID eventId, String eventType, String payload);
}
