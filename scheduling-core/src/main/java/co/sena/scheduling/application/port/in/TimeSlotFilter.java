package co.sena.scheduling.application.port.in;

import co.sena.scheduling.domain.model.ShiftType;

/** Filtros y paginación de la consulta del catálogo. */
public class TimeSlotFilter {

    private final Integer dayOfWeek;
    private final ShiftType shift;
    private final int page;
    private final int pageSize;
    private final String sort;

    public TimeSlotFilter(Integer dayOfWeek, ShiftType shift, int page, int pageSize, String sort) {
        this.dayOfWeek = dayOfWeek;
        this.shift = shift;
        this.page = page;
        this.pageSize = pageSize;
        this.sort = sort;
    }

    public static TimeSlotFilter defaults() {
        return new TimeSlotFilter(null, null, 1, 20, null);
    }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public ShiftType getShift() { return shift; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public String getSort() { return sort; }
}
