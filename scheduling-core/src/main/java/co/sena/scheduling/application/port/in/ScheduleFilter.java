package co.sena.scheduling.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Filtros de GET /schedules (contrato scheduling.yaml). */
public class ScheduleFilter {

    private final UUID fichaId;
    private final String period;
    private final String status;
    private final Instant from;
    private final Instant to;
    private final int page;
    private final int pageSize;
    private final String sort;

    public ScheduleFilter(UUID fichaId, String period, String status,
                          Instant from, Instant to, int page, int pageSize, String sort) {
        this.fichaId = fichaId;
        this.period = period;
        this.status = status;
        this.from = from;
        this.to = to;
        this.page = page <= 0 ? 1 : page;
        this.pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        this.sort = sort;
    }

    public static ScheduleFilter defaults() {
        return new ScheduleFilter(null, null, null, null, null, 1, 20, null);
    }

    public UUID getFichaId() { return fichaId; }
    public String getPeriod() { return period; }
    public String getStatus() { return status; }
    public Instant getFrom() { return from; }
    public Instant getTo() { return to; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public String getSort() { return sort; }
}
