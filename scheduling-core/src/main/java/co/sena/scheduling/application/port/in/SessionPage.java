package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.ClassSession;
import java.util.List;

/** Página de sesiones (envelope data + pagination del contrato). */
public class SessionPage {

    private final List<ClassSession> data;
    private final int page;
    private final int pageSize;
    private final long totalItems;

    public SessionPage(List<ClassSession> data, int page, int pageSize, long totalItems) {
        this.data = data;
        this.page = page;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
    }

    public List<ClassSession> getData() { return data; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotalItems() { return totalItems; }
    public int getTotalPages() {
        return (int) Math.ceil((double) totalItems / (double) Math.max(pageSize, 1));
    }
}
