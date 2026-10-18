package co.sena.scheduling.adapter.out.calendar;

import co.sena.scheduling.application.port.out.HolidayCalendarPort;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Festivos nacionales de Colombia (HU-003 E4, RN-SCH-06). DEUDA documentada:
 * reference-data-service no expone el catálogo aún; al existir, reemplazar
 * por un cliente del catálogo real sin tocar el dominio (el puerto no cambia).
 * Fijos + movibles por cómputo pascual (Ley 51/1983, trasladables al lunes —
 * método determinista, sin dependencias externas).
 */
@Component
public class StaticHolidayCalendar implements HolidayCalendarPort {

    private static final Set<MonthDay> FIXED = Set.of(
        MonthDay.of(1, 1),   // Año Nuevo
        MonthDay.of(5, 1),   // Día del Trabajo
        MonthDay.of(7, 20),  // Independencia
        MonthDay.of(8, 7),   // Batalla de Boyacá
        MonthDay.of(12, 8),  // Inmaculada Concepción
        MonthDay.of(12, 25)  // Navidad
    );

    @Override
    public boolean isHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (FIXED.contains(MonthDay.from(date))) {
            return true;
        }
        return movableOf(date.getYear()).contains(date);
    }

    /** Movibles del año: regla Emiliani (trasladables al lunes) + pascuales. */
    private static Set<LocalDate> movableOf(int year) {
        LocalDate easter = easterSunday(year);
        return Set.of(
            nextMondayOrSame(LocalDate.of(year, 1, 6)),    // Reyes Magos
            nextMondayOrSame(LocalDate.of(year, 3, 19)),   // San José
            easter.minusDays(3),                            // Jueves Santo
            easter.minusDays(2),                            // Viernes Santo
            easter.plusDays(43),                            // Ascensión
            easter.plusDays(64),                            // Corpus Christi
            easter.plusDays(71),                            // Sagrado Corazón
            nextMondayOrSame(LocalDate.of(year, 6, 29)),   // San Pedro y San Pablo
            nextMondayOrSame(LocalDate.of(year, 8, 15)),   // Asunción
            nextMondayOrSame(LocalDate.of(year, 10, 12)),  // Día de la Raza
            nextMondayOrSame(LocalDate.of(year, 11, 1)),   // Todos los Santos
            nextMondayOrSame(LocalDate.of(year, 11, 11))   // Independencia de Cartagena
        );
    }

    private static LocalDate nextMondayOrSame(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY ? date
            : date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua (algoritmo gregoriano anónimo). */
    private static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
