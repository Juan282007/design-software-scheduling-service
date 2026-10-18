package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.Schedule;
import java.util.List;

/** Página de horarios (envelope data + pagination del contrato). */
public class SchedulePage {

    private final List<Schedule> data;
    private final int page;
    private final int pageSize;
    private final long totalItems;

    public SchedulePage(List<Schedule> data, int page, int pageSize, long totalItems) {
        this.data = data;
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    public List<Schedule> getData() { return data; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotalItems() { return totalItems; }
    public int getTotalPages() {
        return (int) Math.ceil((double) totalItems / (double) Math.max(pageSize, 1));
    }
}
