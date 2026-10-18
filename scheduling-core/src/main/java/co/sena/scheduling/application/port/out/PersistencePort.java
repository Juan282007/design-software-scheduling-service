package co.sena.scheduling.application.port.out;

import co.sena.scheduling.application.port.in.ScheduleFilter;
import co.sena.scheduling.application.port.in.SchedulePage;
import co.sena.scheduling.domain.model.Schedule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia para el agregado Schedule.
 */
public interface PersistencePort {

    Schedule save(Schedule schedule);

    Optional<Schedule> findById(UUID id);

    List<Schedule> findByFichaIdAndPeriod(UUID fichaId, String period);

    List<Schedule> findByFichaId(UUID fichaId);

    boolean existsPublishedByFichaIdAndPeriod(UUID fichaId, String period);

    Optional<Schedule> findPublishedByFichaIdAndPeriod(UUID fichaId, String period);

    int archivePublishedByFichaIdAndPeriodExcept(UUID fichaId, String period, UUID scheduleId);

    Optional<Schedule> findActiveById(UUID id);

    SchedulePage search(ScheduleFilter filter);

    SchedulePage search(ScheduleFilter filter, List<UUID> visibleIds);

    SchedulePage search(ScheduleFilter filter, List<UUID> visibleIds, UUID trainingCenterId);

    List<UUID> findActiveScheduleIdsByInstructorId(UUID instructorId);

    boolean fichaBelongsToTrainingCenter(UUID fichaId, UUID trainingCenterId);
}
