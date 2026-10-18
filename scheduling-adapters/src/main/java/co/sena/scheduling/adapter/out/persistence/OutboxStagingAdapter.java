package co.sena.scheduling.adapter.out.persistence;

import co.sena.scheduling.adapter.out.messaging.OutboxMessage;
import co.sena.scheduling.application.port.out.OutboxStagingPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Staging siempre activo (no depende de outbox.enabled; el relay sí). */
@Component
public class OutboxStagingAdapter implements OutboxStagingPort {

    private final OutboxRepository outboxRepository;

    public OutboxStagingAdapter(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void stage(UUID eventId, String eventType, String payload) {
        outboxRepository.save(new OutboxMessage(eventId, eventType, payload));
    }
}
