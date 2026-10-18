package co.sena.scheduling.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** Filtros de GET /sessions y GET /schedules/{id}/sessions (contrato). */
public class SessionFilter {

    private final UUID scheduleId;
    private final UUID instructorId;
    private final UUID environmentId;
    private final String status;
    private final LocalDate from;
    private final LocalDate to;
    private final int page;
    private final int pageSize;
    private final String sort;

    public SessionFilter(UUID scheduleId, UUID instructorId, UUID environmentId,
                         String status, LocalDate from, LocalDate to,
                         int page, int pageSize, String sort) {
        this.scheduleId = scheduleId;
        this.instructorId = instructorId;
        this.environmentId = environmentId;
        this.status = status;
        this.from = from;
        this.to = to;
        this.page = page <= 0 ? 1 : page;
        this.pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        this.sort = sort;
    }

    public UUID getScheduleId() { return scheduleId; }
    public UUID getInstructorId() { return instructorId; }
    public UUID getEnvironmentId() { return environmentId; }
    public String getStatus() { return status; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public String getSort() { return sort; }
}
