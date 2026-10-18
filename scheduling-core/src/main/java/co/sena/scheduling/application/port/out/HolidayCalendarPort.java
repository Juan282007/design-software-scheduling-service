package co.sena.scheduling.application.port.out;

import java.time.LocalDate;

/**
 * Calendario de festivos nacionales (HU-003 E4, RN-SCH-06).
 * Deuda documentada: reference-data-service no expone el catálogo aún;
 * la implementación actual es una lista estática mínima intercambiable
 * por un cliente del catálogo real cuando exista.
 */
public interface HolidayCalendarPort {

    boolean isHoliday(LocalDate date);
}
