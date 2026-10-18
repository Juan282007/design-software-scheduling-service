package co.sena.scheduling.domain.exception;

/** Error de validación del agregado schedule (400). */
public class ScheduleValidationException extends RuntimeException {

    private final String field;
    private final String issue;

    public ScheduleValidationException(String field, String issue, String message) {
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
