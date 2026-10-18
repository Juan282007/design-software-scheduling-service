package co.sena.scheduling.application.port.out;

import co.sena.scheduling.domain.event.ScheduleEvents;

/**
 * Puerto de mensajería para publicar eventos de dominio.
 */
public interface MessagingPort {

    void publishScheduleCreated(ScheduleEvents.ScheduleCreated event);

    void publishScheduleValidated(ScheduleEvents.ScheduleValidated event);

    void publishSchedulePublished(ScheduleEvents.SchedulePublished event);

    void publishClassSessionCreated(ScheduleEvents.ClassSessionCreated event);

    void publishClassSessionCancelled(ScheduleEvents.ClassSessionCancelled event);
}
