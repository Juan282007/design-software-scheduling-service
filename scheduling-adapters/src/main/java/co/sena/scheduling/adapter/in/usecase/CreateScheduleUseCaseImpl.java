package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.CreateScheduleCommand;
import co.sena.scheduling.application.port.in.CreateScheduleUseCase;
import co.sena.scheduling.application.port.out.MessagingPort;
import co.sena.scheduling.application.port.out.PersistencePort;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.Schedule;
import co.sena.scheduling.domain.model.ScheduleStatus;
import co.sena.scheduling.domain.event.ScheduleEvents;
import co.sena.scheduling.domain.service.ScheduleDomainService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementación del use case para crear un borrador de horario.
 */
@Component
public class CreateScheduleUseCaseImpl implements CreateScheduleUseCase {

    private final PersistencePort persistencePort;
    private final MessagingPort messagingPort;
    private final ScheduleDomainService domainService;

    public CreateScheduleUseCaseImpl(PersistencePort persistencePort,
                                     MessagingPort messagingPort,
                                     ScheduleDomainService domainService) {
        this.persistencePort = persistencePort;
        this.messagingPort = messagingPort;
        this.domainService = domainService;
    }

    @Override
    @Transactional
    public Schedule create(CreateScheduleCommand command) {
        if (command == null) {
            throw new ScheduleValidationException("payload", "REQUIRED", "El payload es obligatorio");
        }
        domainService.validateCreate(command.getFichaId(), command.getPeriod(), command.getName());
        Schedule schedule = new Schedule(
            UUID.randomUUID(),
            command.getFichaId(),
            command.getPeriod(),
            command.getName() == null ? null : command.getName().trim(),
            ScheduleStatus.DRAFT,
            command.getCreatedBy()
        );
        schedule.setRowVersion(0);
        schedule.setActive(true);

        Schedule saved;
        try {
            saved = persistencePort.save(schedule);
        } catch (DataIntegrityViolationException race) {
            throw race;
        }

        messagingPort.publishScheduleCreated(
            new ScheduleEvents.ScheduleCreated(
                saved.getId(), saved.getFichaId(), saved.getPeriod(),
                saved.getName(), saved.getCreatedBy(), saved.getCreatedAt()
            )
        );

        return saved;
    }
}
