package co.sena.scheduling.adapter.out.messaging;

import co.sena.scheduling.application.port.out.MessagingPort;
import co.sena.scheduling.adapter.out.persistence.OutboxRepository;
import co.sena.scheduling.domain.event.ScheduleEvents;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Relay del Outbox: hace polling de registros PENDING y los publica.
 * Garantiza entrega at-least-once para scheduling.schedule.published.
 *
 * Desactivado por defecto: la tabla scheduling.outbox aún no está provisionada
 * (ver TODO Nº 8). Se habilita con outbox.enabled=true cuando la HU de mensajería
 * añada el changeset correspondiente en design-software-scheduling-db.
 */
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OutboxRelay implements MessagingPort {

    private final OutboxRepository outboxRepository;
    private final RabbitMQPublisher rabbitMQPublisher;
    private final ObjectMapper objectMapper;

    @Value("${outbox.enabled:true}")
    private boolean outboxEnabled;

    public OutboxRelay(OutboxRepository outboxRepository,
                       RabbitMQPublisher rabbitMQPublisher,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitMQPublisher = rabbitMQPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publishScheduleCreated(ScheduleEvents.ScheduleCreated event) {
        OutboxMessage message = new OutboxMessage(
            UUID.randomUUID(),
            "scheduling.schedule.created",
            serialize(event)
        );
        outboxRepository.save(message);
    }

    @Override
    @Transactional
    public void publishScheduleValidated(ScheduleEvents.ScheduleValidated event) {
        OutboxMessage message = new OutboxMessage(
            UUID.randomUUID(),
            "scheduling.schedule.validated",
            serialize(event)
        );
        outboxRepository.save(message);
    }

    @Override
    @Transactional
    public void publishSchedulePublished(ScheduleEvents.SchedulePublished event) {
        OutboxMessage message = new OutboxMessage(
            UUID.randomUUID(),
            "scheduling.schedule.published",
            serialize(event)
        );
        outboxRepository.save(message);
    }

    @Override
    @Transactional
    public void publishClassSessionCreated(ScheduleEvents.ClassSessionCreated event) {
        OutboxMessage message = new OutboxMessage(
            UUID.randomUUID(),
            "scheduling.class_session.created",
            serialize(event)
        );
        outboxRepository.save(message);
    }

    @Override
    @Transactional
    public void publishClassSessionCancelled(ScheduleEvents.ClassSessionCancelled event) {
        OutboxMessage message = new OutboxMessage(
            UUID.randomUUID(),
            "scheduling.class_session.cancelled",
            serialize(event)
        );
        outboxRepository.save(message);
    }

    /**
     * Polling periódico del Outbox para mensajes PENDING no publicados.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval:2000}")
    @Transactional
    public void pollOutbox() {
        if (!outboxEnabled) {
            return;
        }
        outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING").forEach(message -> {
            try {
                rabbitMQPublisher.publish("scheduling-events", message.getPayload());
                message.markPublished();
                outboxRepository.save(message);
            } catch (Exception e) {
                message.markFailed();
                outboxRepository.save(message);
            }
        });
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible serializar evento outbox", ex);
        }
    }
}
