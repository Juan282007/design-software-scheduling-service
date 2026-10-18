package co.sena.scheduling.domain.service;

import co.sena.scheduling.domain.exception.TimeSlotValidationException;
import co.sena.scheduling.domain.model.ShiftType;

import java.time.LocalTime;
import java.util.Locale;

/** Reglas puras del catálogo de franjas horarias. */
public class TimeSlotDomainService {

    public void validate(String name, int dayOfWeek, LocalTime startTime,
                         LocalTime endTime, ShiftType shift) {
        if (name == null || name.isBlank()) {
            throw invalid("name", "REQUIRED", "name es obligatorio");
        }
        if (name.length() > 50) {
            throw invalid("name", "MAX_LENGTH", "name no puede superar 50 caracteres");
        }
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw invalid("day_of_week", "OUT_OF_RANGE", "day_of_week debe estar entre 1 y 7");
        }
        if (startTime == null || endTime == null) {
            throw invalid("start_time", "REQUIRED", "start_time y end_time son obligatorios");
        }
        if (!startTime.isBefore(endTime)) {
            throw invalid("start_time", "INVALID_RANGE", "start_time debe ser menor que end_time");
        }
        if (shift == null) {
            throw invalid("shift", "REQUIRED", "shift es obligatorio");
        }
    }

    public ShiftType normalizeShift(String rawShift) {
        if (rawShift == null || rawShift.isBlank()) {
            throw invalid("shift", "REQUIRED", "shift es obligatorio");
        }
        try {
            return ShiftType.valueOf(rawShift.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("shift", "INVALID_VALUE", "shift debe ser DAY, NIGHT o MIXED");
        }
    }

    private TimeSlotValidationException invalid(String field, String issue, String message) {
        return new TimeSlotValidationException(field, issue, message);
    }
}
