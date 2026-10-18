package co.sena.scheduling.adapter.in.usecase;

import co.sena.scheduling.application.port.in.ListConflictsUseCase;
import co.sena.scheduling.application.port.out.SchedulingConflictPort;
import co.sena.scheduling.domain.model.ConflictType;
import co.sena.scheduling.domain.model.SchedulingConflict;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** HU-SCH-005 E5: listado paginado con filtros is_resolved y conflict_type. */
@Component
public class ListConflictsUseCaseImpl implements ListConflictsUseCase {

    private final SchedulingConflictPort conflictPort;

    public ListConflictsUseCaseImpl(SchedulingConflictPort conflictPort) {
        this.conflictPort = conflictPort;
    }

    @Override
    public Result listConflicts(UUID scheduleId, Boolean isResolved, String conflictType,
                                int page, int pageSize) {
        if (scheduleId == null) {
            return new Result(List.of(), 0);
        }
        List<SchedulingConflict> conflicts = conflictPort.findByScheduleId(scheduleId);

        if (isResolved != null) {
            conflicts = conflicts.stream()
                    .filter(c -> c.isResolved() == isResolved)
                    .toList();
        }

        if (conflictType != null && !conflictType.isBlank()) {
            ConflictType type;
            try {
                type = ConflictType.valueOf(conflictType);
            } catch (IllegalArgumentException ex) {
                // Mismo patrón que ?status= en GET /schedules: 400 VALIDATION_ERROR
                throw new co.sena.scheduling.domain.exception.ScheduleValidationException(
                    "conflict_type", "INVALID_VALUE",
                    "conflict_type debe ser uno de los 6 valores del contrato");
            }
            final ConflictType expected = type;
            conflicts = conflicts.stream()
                    .filter(c -> c.getConflictType() == expected)
                    .toList();
        }

        long totalItems = conflicts.size();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int start = (safePage - 1) * safePageSize;
        if (start >= conflicts.size()) {
            return new Result(List.of(), totalItems);
        }
        int end = Math.min(start + safePageSize, conflicts.size());
        return new Result(conflicts.subList(start, end), totalItems);
    }
}
