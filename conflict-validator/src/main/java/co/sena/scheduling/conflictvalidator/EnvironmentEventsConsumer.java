package co.sena.scheduling.conflictvalidator;

import co.sena.scheduling.domain.service.AvailabilityProjectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-SCH-004: Consumer for environment-events (ADR-002 E1/E4).
 * Handles the documented JSON envelope and delegates to AvailabilityProjectionService with idempotency.
 * Envelope: {event, version, source, event_id, timestamp, data: {environment_id, ...}}
 */
@Component
public class EnvironmentEventsConsumer {

    private final AvailabilityProjectionService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnvironmentEventsConsumer(AvailabilityProjectionService service) {
        this.service = service;
    }

    /**
     * Entry point for broker messages — receives raw JSON envelope.
     * @return true if applied, false if deduplicated
     */
    @RabbitListener(queuesToDeclare = @Queue("environment-events"))
    public boolean handle(String jsonEnvelope) {
        try {
            JsonNode root = mapper.readTree(jsonEnvelope);
            String event = root.path("event").asText();
            UUID eventId = UUID.fromString(root.path("event_id").asText());
            JsonNode data = root.path("data");

            if ("environment.availability.changed".equals(event)) {
                UUID envId = UUID.fromString(data.path("environment_id").asText());
                boolean available = data.path("available").asBoolean();
                Instant effectiveFrom = Instant.parse(data.path("effective_from").asText());
                String reason = data.path("reason").asText(null);
                return service.handleAvailabilityChanged(eventId, envId, available, effectiveFrom, reason);
            } else if ("environment.maintenance.started".equals(event)) {
                UUID envId = UUID.fromString(data.path("environment_id").asText());
                // payload uses start_date/end_date as DATE strings
                Instant start = Instant.parse(data.path("start_date").asText() + "T00:00:00Z");
                Instant end = Instant.parse(data.path("end_date").asText() + "T00:00:00Z");
                String desc = data.path("description").asText(null);
                return service.handleMaintenanceStartedBool(eventId, envId, start, end, desc);
            }
            return false;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid envelope: " + e.getMessage(), e);
        }
    }
}
