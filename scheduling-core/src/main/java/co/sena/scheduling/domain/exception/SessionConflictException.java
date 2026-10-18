package co.sena.scheduling.domain.exception;

/**
 * Conflicto de programación de una sesión (409). El código es uno de
 * INSTRUCTOR_DOUBLE_BOOKED / ENVIRONMENT_DOUBLE_BOOKED (red EXCLUDE de BD, E2)
 * o SCHEDULE_IMMUTABLE (padre no DRAFT).
 */
public class SessionConflictException extends RuntimeException {

    private final String code;

    public SessionConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
