package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.ScheduleReportUseCase;
import co.sena.scheduling.application.port.out.ScheduleReportPersistencePort;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.model.ScheduleStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Component
public class ScheduleReportUseCaseImpl implements ScheduleReportUseCase {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_LIMIT = 50;
    private static final Set<String> CONFLICT_TYPES = Set.of(
            "INSTRUCTOR_DOUBLE_BOOKED",
            "ENVIRONMENT_DOUBLE_BOOKED",
            "SESSIONS_OVERLAP",
            "ENVIRONMENT_MAINTENANCE",
            "INSTRUCTOR_UNAVAILABLE",
            "INSTRUCTOR_TRAVEL_CONFLICT");

    private final ScheduleReportPersistencePort persistence;

    public ScheduleReportUseCaseImpl(ScheduleReportPersistencePort persistence) {
        this.persistence = persistence;
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<ScheduleByInstructor> scheduleByInstructor(
            UUID instructorId, LocalDate from, LocalDate to, int page, int pageSize, String sort) {
        validateDateRange(from, to);
        return persistence.scheduleByInstructor(instructorId, from, to, safePage(page), safePageSize(pageSize), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<ScheduleByFicha> scheduleByFicha(
            UUID fichaId, String period, String status, Instant from, Instant to, int page, int pageSize, String sort) {
        validateInstantRange(from, to);
        if (status != null && !status.isBlank()) {
            try {
                ScheduleStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new ScheduleValidationException("status", "INVALID_VALUE",
                        "status debe ser DRAFT, UNDER_REVIEW, PUBLISHED o ARCHIVED");
            }
        }
        return persistence.scheduleByFicha(fichaId, period, status, from, to, safePage(page), safePageSize(pageSize), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<ScheduleByEnvironment> scheduleByEnvironment(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort) {
        validateDateRange(from, to);
        return persistence.scheduleByEnvironment(environmentId, from, to, safePage(page), safePageSize(pageSize), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public OffsetPage<EnvironmentOccupancy> environmentOccupancy(
            UUID environmentId, LocalDate from, LocalDate to, int page, int pageSize, String sort) {
        validateDateRange(from, to);
        return persistence.environmentOccupancy(environmentId, from, to, safePage(page), safePageSize(pageSize), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ScheduleConflictReport> scheduleConflicts(
            String cursor, int limit, UUID scheduleId, String conflictType, Boolean resolved, Instant from, Instant to) {
        validateInstantRange(from, to);
        if (conflictType != null && !CONFLICT_TYPES.contains(conflictType)) {
            throw new ScheduleValidationException("conflict_type", "INVALID_VALUE",
                    "conflict_type no esta permitido");
        }
        return persistence.scheduleConflicts(cursor, safeLimit(limit), scheduleId, conflictType, resolved, from, to);
    }

    private int safePage(int page) {
        int effective = page == 0 ? DEFAULT_PAGE : page;
        if (effective < 1) {
            throw new ScheduleValidationException("page", "OUT_OF_RANGE", "page debe ser mayor o igual a 1");
        }
        return effective;
    }

    private int safePageSize(int pageSize) {
        int effective = pageSize == 0 ? DEFAULT_PAGE_SIZE : pageSize;
        if (effective < 1 || effective > MAX_PAGE_SIZE) {
            throw new ScheduleValidationException("page_size", "OUT_OF_RANGE", "page_size debe estar entre 1 y 100");
        }
        return effective;
    }

    private int safeLimit(int limit) {
        int effective = limit == 0 ? DEFAULT_LIMIT : limit;
        if (effective < 1 || effective > MAX_PAGE_SIZE) {
            throw new ScheduleValidationException("limit", "OUT_OF_RANGE", "limit debe estar entre 1 y 100");
        }
        return effective;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ScheduleValidationException("to", "INVALID_RANGE", "to debe ser posterior a from");
        }
    }

    private void validateInstantRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ScheduleValidationException("to", "INVALID_RANGE", "to debe ser posterior a from");
        }
    }
}
