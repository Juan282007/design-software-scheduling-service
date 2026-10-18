package co.sena.scheduling.domain.exception;

/** El horario PUBLISHED es inmutable (409 SCHEDULE_IMMUTABLE). */
public class ScheduleImmutableException extends RuntimeException {

    public ScheduleImmutableException(String message) {
        super(message);
    }
}
