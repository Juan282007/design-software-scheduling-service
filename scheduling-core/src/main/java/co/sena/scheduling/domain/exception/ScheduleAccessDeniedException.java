package co.sena.scheduling.domain.exception;

/** Scope no resoluble o sin ownership (403 FORBIDDEN, fail-closed QA PR #7). */
public class ScheduleAccessDeniedException extends RuntimeException {

    public ScheduleAccessDeniedException(String message) {
        super(message);
    }
}
