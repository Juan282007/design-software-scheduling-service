package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.TimeSlot;

import java.util.UUID;

/** Puerto de entrada del catálogo de franjas horarias. */
public interface TimeSlotUseCase {

    TimeSlot create(CreateTimeSlotCommand command);

    TimeSlot update(UUID id, UpdateTimeSlotCommand command);

    TimeSlot get(UUID id);

    TimeSlotPage list(TimeSlotFilter filter);

    void delete(UUID id, UUID deletedBy);
}
