package co.sena.scheduling.application.port.out;

import co.sena.scheduling.application.port.in.ScheduleReportUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface ScheduleReportPersistencePort {

    ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.ScheduleByInstructor> scheduleByInstructor(
            UUID instructorId, LocalDate from, LocalDate to, int page, int pageSize, String sort);

    ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.ScheduleByFicha> scheduleByFicha(
            UUID fichaId, String period, String status, Instant from, Instant to, int page, int pageSize, String sort);

    ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.ScheduleByEnvironment> scheduleByEnvironment(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort);

    ScheduleReportUseCase.OffsetPage<ScheduleReportUseCase.EnvironmentOccupancy> environmentOccupancy(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort);

    ScheduleReportUseCase.CursorPage<ScheduleReportUseCase.ScheduleConflictReport> scheduleConflicts(
            String cursor, int limit, UUID scheduleId, String conflictType, Boolean resolved, Instant from, Instant to);
}
