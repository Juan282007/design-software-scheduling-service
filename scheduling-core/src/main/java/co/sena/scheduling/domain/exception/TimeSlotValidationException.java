package co.sena.scheduling.domain.exception;

/** Error de validación de una franja horaria. */
public class TimeSlotValidationException extends RuntimeException {

    private final String field;
    private final String issue;

    public TimeSlotValidationException(String field, String issue, String message) {
        super(message);
        this.field = field;
        this.issue = issue;
    }

    public String getField() {
        return field;
    }

    public String getIssue() {
        return issue;
    }
}
