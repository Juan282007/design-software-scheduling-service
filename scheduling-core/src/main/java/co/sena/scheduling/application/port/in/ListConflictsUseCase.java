package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.SchedulingConflict;
import java.util.List;
import java.util.UUID;

/**
 * Use case para listar conflictos de un horario con filtros y paginación (E5).
 */
public interface ListConflictsUseCase {

    /** Página de conflictos + total tras aplicar los filtros. */
    record Result(List<SchedulingConflict> items, long totalItems) {}

    Result listConflicts(UUID scheduleId, Boolean isResolved, String conflictType, int page, int pageSize);
}
