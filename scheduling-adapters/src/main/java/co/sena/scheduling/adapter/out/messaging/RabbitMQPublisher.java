package co.sena.scheduling.adapter.out.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publicador AMQP (RabbitMQ) para eventos de dominio.
 * El topic es scheduling-events.
 */
@Component
public class RabbitMQPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String exchange, String payload) {
        rabbitTemplate.convertAndSend(exchange, payload);
    }
}
