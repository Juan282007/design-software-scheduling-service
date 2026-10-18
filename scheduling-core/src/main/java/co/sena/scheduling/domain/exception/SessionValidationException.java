package co.sena.scheduling.domain.exception;

/** Error de validación de una sesión de clase (400). */
public class SessionValidationException extends RuntimeException {

    private final String field;
    private final String issue;

    public SessionValidationException(String field, String issue, String message) {
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
