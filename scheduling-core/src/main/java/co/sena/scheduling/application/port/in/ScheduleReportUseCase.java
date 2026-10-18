package co.sena.scheduling.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ScheduleReportUseCase {

    OffsetPage<ScheduleByInstructor> scheduleByInstructor(
            UUID instructorId, LocalDate from, LocalDate to, int page, int pageSize, String sort);

    OffsetPage<ScheduleByFicha> scheduleByFicha(
            UUID fichaId, String period, String status, Instant from, Instant to, int page, int pageSize, String sort);

    OffsetPage<ScheduleByEnvironment> scheduleByEnvironment(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort);

    OffsetPage<EnvironmentOccupancy> environmentOccupancy(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort);

    CursorPage<ScheduleConflictReport> scheduleConflicts(
            String cursor, int limit, UUID scheduleId, String conflictType, Boolean resolved, Instant from, Instant to);

    record OffsetPage<T>(List<T> data, int page, int pageSize, long totalItems) {
        public int totalPages() {
            return pageSize == 0 ? 0 : (int) ((totalItems + pageSize - 1) / pageSize);
        }
    }

    record CursorPage<T>(List<T> data, String nextCursor, int limit, boolean hasMore) {}

    record ScheduleByInstructor(
            UUID instructorId,
            long scheduleCount,
            long sessionCount,
            BigDecimal totalHours) {}

    record ScheduleByFicha(
            UUID fichaId,
            UUID scheduleId,
            String period,
            String status,
            long sessionCount,
            long instructorCount,
            long environmentCount) {}

    record ScheduleByEnvironment(
            UUID environmentId,
            long sessionCount,
            BigDecimal totalHours,
            long fichaCount) {}

    record EnvironmentOccupancy(
            UUID environmentId,
            LocalDate periodFrom,
            LocalDate periodTo,
            long occupiedSlots,
            long totalSlots,
            BigDecimal occupancyRate) {}

    record ScheduleConflictReport(
            UUID id,
            UUID scheduleId,
            UUID sessionAId,
            UUID sessionBId,
            String conflictType,
            String description,
            boolean resolved,
            Instant detectedAt) {}
}
