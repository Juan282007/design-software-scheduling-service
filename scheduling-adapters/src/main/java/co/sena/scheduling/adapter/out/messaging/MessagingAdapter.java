package co.sena.scheduling.adapter.out.messaging;

import co.sena.scheduling.application.port.out.MessagingPort;
import co.sena.scheduling.domain.event.ScheduleEvents;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Adapter de mensajería. Para desarrollo local sin broker,
 * las implementaciones de OutboxRelay publican directamente.
 * En producción, usa RabbitMQ vía Outbox pattern.
 *
 * Requiere OutboxRelay (outbox.enabled=true); con mensajería desactivada
 * el puerto lo sirve DisabledMessagingAdapter.
 */
@Component
@Primary
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class MessagingAdapter implements MessagingPort {

    private final OutboxRelay outboxRelay;

    public MessagingAdapter(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Override
    public void publishScheduleCreated(ScheduleEvents.ScheduleCreated event) {
        outboxRelay.publishScheduleCreated(event);
    }

    @Override
    public void publishScheduleValidated(ScheduleEvents.ScheduleValidated event) {
        outboxRelay.publishScheduleValidated(event);
    }

    @Override
    public void publishSchedulePublished(ScheduleEvents.SchedulePublished event) {
        outboxRelay.publishSchedulePublished(event);
    }

    @Override
    public void publishClassSessionCreated(ScheduleEvents.ClassSessionCreated event) {
        outboxRelay.publishClassSessionCreated(event);
    }

    @Override
    public void publishClassSessionCancelled(ScheduleEvents.ClassSessionCancelled event) {
        outboxRelay.publishClassSessionCancelled(event);
    }
}
