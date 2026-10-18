package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.application.port.in.ScheduleReportUseCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ScheduleReportUseCase reports;

    public ReportController(ScheduleReportUseCase reports) {
        this.reports = reports;
    }

    @GetMapping("/schedule-by-instructor")
    @PreAuthorize("hasAuthority('SCH_REPORT_VIEW')")
    public ResponseEntity<OffsetResponse<ScheduleByInstructorResponse>> scheduleByInstructor(
            @RequestParam(name = "instructor_id", required = false) UUID instructorId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        var result = reports.scheduleByInstructor(instructorId, from, to, page, pageSize, sort);
        return ResponseEntity.ok(OffsetResponse.from(result,
                result.data().stream().map(ScheduleByInstructorResponse::from).toList()));
    }

    @GetMapping("/schedule-by-ficha")
    @PreAuthorize("hasAuthority('SCH_REPORT_VIEW')")
    public ResponseEntity<OffsetResponse<ScheduleByFichaResponse>> scheduleByFicha(
            @RequestParam(name = "ficha_id", required = false) UUID fichaId,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        var result = reports.scheduleByFicha(fichaId, period, status, from, to, page, pageSize, sort);
        return ResponseEntity.ok(OffsetResponse.from(result,
                result.data().stream().map(ScheduleByFichaResponse::from).toList()));
    }

    @GetMapping("/schedule-by-environment")
    @PreAuthorize("hasAuthority('SCH_REPORT_VIEW')")
    public ResponseEntity<OffsetResponse<ScheduleByEnvironmentResponse>> scheduleByEnvironment(
            @RequestParam(name = "environment_id", required = false) UUID environmentId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        var result = reports.scheduleByEnvironment(environmentId, from, to, page, pageSize, sort);
        return ResponseEntity.ok(OffsetResponse.from(result,
                result.data().stream().map(ScheduleByEnvironmentResponse::from).toList()));
    }

    @GetMapping("/environment-occupancy")
    @PreAuthorize("hasAuthority('SCH_REPORT_VIEW')")
    public ResponseEntity<OffsetResponse<EnvironmentOccupancyResponse>> environmentOccupancy(
            @RequestParam(name = "environment_id", required = false) UUID environmentId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "page_size", required = false, defaultValue = "20") int pageSize,
            @RequestParam(name = "sort", required = false) String sort) {
        var result = reports.environmentOccupancy(environmentId, from, to, page, pageSize, sort);
        return ResponseEntity.ok(OffsetResponse.from(result,
                result.data().stream().map(EnvironmentOccupancyResponse::from).toList()));
    }

    @GetMapping("/schedule-conflicts")
    @PreAuthorize("hasAuthority('SCH_REPORT_VIEW')")
    public ResponseEntity<CursorResponse<ConflictResponse>> scheduleConflicts(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            @RequestParam(name = "schedule_id", required = false) UUID scheduleId,
            @RequestParam(name = "conflict_type", required = false) String conflictType,
            @RequestParam(name = "is_resolved", required = false) Boolean resolved,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        var result = reports.scheduleConflicts(cursor, limit, scheduleId, conflictType, resolved, from, to);
        return ResponseEntity.ok(CursorResponse.from(result,
                result.data().stream().map(ConflictResponse::from).toList()));
    }

    public record OffsetResponse<T>(List<T> data, Pagination pagination) {
        static <T, S> OffsetResponse<T> from(ScheduleReportUseCase.OffsetPage<S> page, List<T> data) {
            return new OffsetResponse<>(data,
                    new Pagination(page.page(), page.pageSize(), page.totalItems(), page.totalPages()));
        }
    }

    public record Pagination(
            int page,
            @JsonProperty("page_size") int pageSize,
            @JsonProperty("total_items") long totalItems,
            @JsonProperty("total_pages") int totalPages) {}

    public record CursorResponse<T>(List<T> data, CursorPagination pagination) {
        static <T, S> CursorResponse<T> from(ScheduleReportUseCase.CursorPage<S> page, List<T> data) {
            return new CursorResponse<>(data,
                    new CursorPagination(page.nextCursor(), page.limit(), page.hasMore()));
        }
    }

    public record CursorPagination(
            @JsonProperty("next_cursor") @JsonInclude(JsonInclude.Include.ALWAYS) String nextCursor,
            int limit,
            @JsonProperty("has_more") boolean hasMore) {}

    public record ScheduleByInstructorResponse(
            @JsonProperty("instructor_id") UUID instructorId,
            @JsonProperty("schedule_count") long scheduleCount,
            @JsonProperty("session_count") long sessionCount,
            @JsonProperty("total_hours") BigDecimal totalHours) {
        static ScheduleByInstructorResponse from(ScheduleReportUseCase.ScheduleByInstructor item) {
            return new ScheduleByInstructorResponse(item.instructorId(), item.scheduleCount(),
                    item.sessionCount(), item.totalHours());
        }
    }

    public record ScheduleByFichaResponse(
            @JsonProperty("ficha_id") UUID fichaId,
            @JsonProperty("schedule_id") UUID scheduleId,
            String period,
            String status,
            @JsonProperty("session_count") long sessionCount,
            @JsonProperty("instructor_count") long instructorCount,
            @JsonProperty("environment_count") long environmentCount) {
        static ScheduleByFichaResponse from(ScheduleReportUseCase.ScheduleByFicha item) {
            return new ScheduleByFichaResponse(item.fichaId(), item.scheduleId(), item.period(), item.status(),
                    item.sessionCount(), item.instructorCount(), item.environmentCount());
        }
    }

    public record ScheduleByEnvironmentResponse(
            @JsonProperty("environment_id") UUID environmentId,
            @JsonProperty("session_count") long sessionCount,
            @JsonProperty("total_hours") BigDecimal totalHours,
            @JsonProperty("ficha_count") long fichaCount) {
        static ScheduleByEnvironmentResponse from(ScheduleReportUseCase.ScheduleByEnvironment item) {
            return new ScheduleByEnvironmentResponse(item.environmentId(), item.sessionCount(),
                    item.totalHours(), item.fichaCount());
        }
    }

    public record EnvironmentOccupancyResponse(
            @JsonProperty("environment_id") UUID environmentId,
            @JsonProperty("period_from") LocalDate periodFrom,
            @JsonProperty("period_to") LocalDate periodTo,
            @JsonProperty("occupied_slots") long occupiedSlots,
            @JsonProperty("total_slots") long totalSlots,
            @JsonProperty("occupancy_rate") BigDecimal occupancyRate) {
        static EnvironmentOccupancyResponse from(ScheduleReportUseCase.EnvironmentOccupancy item) {
            return new EnvironmentOccupancyResponse(item.environmentId(), item.periodFrom(), item.periodTo(),
                    item.occupiedSlots(), item.totalSlots(), item.occupancyRate());
        }
    }

    public record ConflictResponse(
            UUID id,
            @JsonProperty("schedule_id") UUID scheduleId,
            @JsonProperty("session_a_id") UUID sessionAId,
            @JsonProperty("session_b_id") UUID sessionBId,
            @JsonProperty("conflict_type") String conflictType,
            String description,
            @JsonProperty("is_resolved") boolean resolved,
            @JsonProperty("detected_at") Instant detectedAt) {
        static ConflictResponse from(ScheduleReportUseCase.ScheduleConflictReport item) {
            return new ConflictResponse(item.id(), item.scheduleId(), item.sessionAId(), item.sessionBId(),
                    item.conflictType(), item.description(), item.resolved(), item.detectedAt());
        }
    }
}
