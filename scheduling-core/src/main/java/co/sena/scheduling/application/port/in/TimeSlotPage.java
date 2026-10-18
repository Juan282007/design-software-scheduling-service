package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.TimeSlot;

import java.util.List;

/** Página de franjas activas para la respuesta REST. */
public class TimeSlotPage {

    private final List<TimeSlot> data;
    private final int page;
    private final int pageSize;
    private final long totalItems;
    private final int totalPages;

    public TimeSlotPage(List<TimeSlot> data, int page, int pageSize, long totalItems) {
        this.data = List.copyOf(data);
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
        this.totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / pageSize);
    }

    public List<TimeSlot> getData() { return data; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotalItems() { return totalItems; }
    public int getTotalPages() { return totalPages; }
}
