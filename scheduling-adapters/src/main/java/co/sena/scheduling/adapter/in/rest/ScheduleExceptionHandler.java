package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.domain.exception.RowVersionMismatchException;
import co.sena.scheduling.domain.exception.ScheduleAccessDeniedException;
import co.sena.scheduling.domain.exception.ScheduleImmutableException;
import co.sena.scheduling.domain.exception.ScheduleNotFoundException;
import co.sena.scheduling.domain.exception.SchedulePublishedExistsException;
import co.sena.scheduling.domain.exception.ScheduleValidationException;
import co.sena.scheduling.domain.exception.UnresolvedConflictsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Traduce errores HU-002 al envelope Error con trace_id (patrón TimeSlotExceptionHandler). */
@RestControllerAdvice(assignableTypes = ScheduleController.class)
public class ScheduleExceptionHandler {

    @ExceptionHandler(ScheduleValidationException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleValidation(ScheduleValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            TimeSlotExceptionHandler.ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(),
                List.of(new TimeSlotExceptionHandler.ErrorDetail(ex.getField(), ex.getIssue()))));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleNotFound(ScheduleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            TimeSlotExceptionHandler.ErrorResponse.of("SCHEDULE_NOT_FOUND", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(SchedulePublishedExistsException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handlePublishedExists(SchedulePublishedExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("SCHEDULE_PUBLISHED_EXISTS", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ScheduleImmutableException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleImmutable(ScheduleImmutableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("SCHEDULE_IMMUTABLE", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(RowVersionMismatchException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleRowVersion(RowVersionMismatchException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("ROW_VERSION_MISMATCH", ex.getMessage(), List.of()));
    }

    /** E3: publicar con conflictos bloqueantes sin resolver → 409 UNRESOLVED_CONFLICTS. */
    @ExceptionHandler(UnresolvedConflictsException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleUnresolved(UnresolvedConflictsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("UNRESOLVED_CONFLICTS", ex.getMessage(), List.of()));
    }

    /** Fail-closed de scope E2 (QA PR #7): 403 FORBIDDEN del contrato. */
    @ExceptionHandler(ScheduleAccessDeniedException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleDenied(ScheduleAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            TimeSlotExceptionHandler.ErrorResponse.of("FORBIDDEN", ex.getMessage(), List.of()));
    }

    /** E5: el índice parcial es la red final; cualquier violación concurrente es 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleRace(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("SCHEDULE_PUBLISHED_EXISTS",
                "Conflicto de unicidad (ficha_id, period) en PUBLISHED.", List.of()));
    }
}
