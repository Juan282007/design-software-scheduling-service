package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.domain.exception.ScheduleAccessDeniedException;
import co.sena.scheduling.domain.exception.ScheduleImmutableException;
import co.sena.scheduling.domain.exception.ScheduleNotFoundException;
import co.sena.scheduling.domain.exception.SessionConflictException;
import co.sena.scheduling.domain.exception.SessionNotFoundException;
import co.sena.scheduling.domain.exception.SessionValidationException;
import co.sena.scheduling.domain.exception.TimeSlotNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Traduce errores HU-003 al envelope Error con trace_id (patrón TimeSlot). */
@RestControllerAdvice(assignableTypes = SessionController.class)
public class SessionExceptionHandler {

    @ExceptionHandler(SessionValidationException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleValidation(SessionValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            TimeSlotExceptionHandler.ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(),
                List.of(new TimeSlotExceptionHandler.ErrorDetail(ex.getField(), ex.getIssue()))));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleNotFound(SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            TimeSlotExceptionHandler.ErrorResponse.of("SESSION_NOT_FOUND", ex.getMessage(), List.of()));
    }

    @ExceptionHandler({ScheduleNotFoundException.class, TimeSlotNotFoundException.class})
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleParentNotFound(RuntimeException ex) {
        String code = ex instanceof TimeSlotNotFoundException ? "TIME_SLOT_NOT_FOUND" : "SCHEDULE_NOT_FOUND";
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            TimeSlotExceptionHandler.ErrorResponse.of(code, ex.getMessage(), List.of()));
    }

    @ExceptionHandler(SessionConflictException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleConflict(SessionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of(ex.getCode(), ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ScheduleImmutableException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleImmutable(ScheduleImmutableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("SCHEDULE_IMMUTABLE", ex.getMessage(), List.of()));
    }

    @ExceptionHandler(ScheduleAccessDeniedException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleDenied(ScheduleAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            TimeSlotExceptionHandler.ErrorResponse.of("FORBIDDEN", ex.getMessage(), List.of()));
    }

    /** Red final E2: mapea el constraint aunque la violación salte en el commit. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleRace(DataIntegrityViolationException ex) {
        co.sena.scheduling.domain.exception.SessionConflictException mapped =
            co.sena.scheduling.adapter.in.usecase.SessionConstraintMapper.toConflict(ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of(mapped.getCode(), mapped.getMessage(), List.of()));
    }
}
