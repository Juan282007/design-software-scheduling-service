package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.domain.exception.ConflictAlreadyResolvedException;
import co.sena.scheduling.domain.exception.ConflictNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Traduce errores HU-SCH-006 al envelope Error (patrón ScheduleExceptionHandler). */
@RestControllerAdvice(assignableTypes = ConflictController.class)
public class ConflictExceptionHandler {

    @ExceptionHandler(ConflictNotFoundException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleNotFound(ConflictNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            TimeSlotExceptionHandler.ErrorResponse.of("CONFLICT_NOT_FOUND", ex.getMessage(), List.of()));
    }

    /** E1: 409 si el conflicto ya estaba resuelto. */
    @ExceptionHandler(ConflictAlreadyResolvedException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleAlreadyResolved(
            ConflictAlreadyResolvedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            TimeSlotExceptionHandler.ErrorResponse.of("CONFLICT_ALREADY_RESOLVED", ex.getMessage(),
                List.of()));
    }
}
