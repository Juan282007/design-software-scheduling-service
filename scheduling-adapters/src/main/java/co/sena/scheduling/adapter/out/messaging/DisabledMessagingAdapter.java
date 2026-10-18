package co.sena.scheduling.adapter.out.messaging;

import co.sena.scheduling.application.port.out.MessagingPort;
import co.sena.scheduling.domain.event.ScheduleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador nulo del puerto de mensajería mientras el Outbox/RabbitMQ no sean
 * parte del alcance (HU de mensajería + tabla scheduling.outbox pendientes,
 * ver TODO Nº 8). Permite que los casos de uso del agregado Schedule se
 * sigan cableando sin publicar eventos ni requerir infraestructura.
 */
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMessagingAdapter implements MessagingPort {

    private static final Logger log = LoggerFactory.getLogger(DisabledMessagingAdapter.class);

    @Override
    public void publishScheduleCreated(ScheduleEvents.ScheduleCreated event) {
        skip("schedule.created", event.getScheduleId());
    }

    @Override
    public void publishScheduleValidated(ScheduleEvents.ScheduleValidated event) {
        skip("schedule.validated", event.getScheduleId());
    }

    @Override
    public void publishSchedulePublished(ScheduleEvents.SchedulePublished event) {
        skip("schedule.published", event.getScheduleId());
    }

    @Override
    public void publishClassSessionCreated(ScheduleEvents.ClassSessionCreated event) {
        skip("class_session.created", event.getScheduleId());
    }

    @Override
    public void publishClassSessionCancelled(ScheduleEvents.ClassSessionCancelled event) {
        skip("class_session.cancelled", event.getScheduleId());
    }

    private void skip(String eventType, Object aggregateId) {
        log.debug("Mensajería deshabilitada (outbox.enabled=false): evento {} para {} no publicado",
            eventType, aggregateId);
    }
}
