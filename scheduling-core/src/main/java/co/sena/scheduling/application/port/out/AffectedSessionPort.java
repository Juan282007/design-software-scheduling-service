package co.sena.scheduling.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HU-SCH-004 E2: Port to find affected future ACTIVE sessions for a maintenance window.
 */
public interface AffectedSessionPort {
    List<UUID> findActiveSessionIdsByEnvironmentIdAndDateRange(UUID environmentId, LocalDate startDate, LocalDate endDate);
}
