package co.sena.scheduling.domain.service;

import co.sena.scheduling.application.port.out.HolidayCalendarPort;
import co.sena.scheduling.domain.exception.SessionValidationException;
import co.sena.scheduling.domain.model.TimeSlot;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/** Reglas puras de sesiones de clase (HU-003 E1/E3/E4). */
public class SessionDomainService {

    private final HolidayCalendarPort holidays;

    public SessionDomainService(HolidayCalendarPort holidays) {
        this.holidays = holidays;
    }

    public void validateReferences(UUID competencyId, UUID environmentId,
                                   UUID instructorId, UUID timeSlotId, LocalDate sessionDate) {
        if (competencyId == null) {
            throw invalid("competency_id", "REQUIRED", "competency_id es obligatorio");
        }
        if (environmentId == null) {
            throw invalid("environment_id", "REQUIRED", "environment_id es obligatorio");
        }
        if (instructorId == null) {
            throw invalid("instructor_id", "REQUIRED", "instructor_id es obligatorio");
        }
        if (timeSlotId == null) {
            throw invalid("time_slot_id", "REQUIRED", "time_slot_id es obligatorio");
        }
        if (sessionDate == null) {
            throw invalid("session_date", "REQUIRED", "session_date es obligatoria");
        }
    }

    /** E3 (RN-SCH-05): 1-6 horas. E4 (RN-SCH-06): sin domingo ni festivos. */
    public void validateSlotWindow(TimeSlot slot, LocalDate sessionDate) {
        long minutes = Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
        if (minutes < 60 || minutes > 360) {
            throw invalid("time_slot_id", "INVALID_DURATION",
                "La franja debe durar entre 1 y 6 horas");
        }
        if (slot.getDayOfWeek() == 7 || sessionDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            throw invalid("session_date", "SUNDAY_NOT_ALLOWED",
                "No se aceptan sesiones en domingo");
        }
        if (sessionDate.getDayOfWeek().getValue() != slot.getDayOfWeek()) {
            throw invalid("session_date", "DAY_MISMATCH",
                "La fecha no cae en el día de la franja");
        }
        if (holidays != null && holidays.isHoliday(sessionDate)) {
            throw invalid("session_date", "HOLIDAY_NOT_ALLOWED",
                "No se aceptan sesiones en festivos nacionales");
        }
    }

    private SessionValidationException invalid(String field, String issue, String message) {
        return new SessionValidationException(field, issue, message);
    }
}
