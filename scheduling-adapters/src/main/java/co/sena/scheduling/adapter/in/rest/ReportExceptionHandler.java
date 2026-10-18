package co.sena.scheduling.adapter.in.rest;

import co.sena.scheduling.domain.exception.ScheduleValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = ReportController.class)
public class ReportExceptionHandler {

    @ExceptionHandler(ScheduleValidationException.class)
    ResponseEntity<TimeSlotExceptionHandler.ErrorResponse> handleValidation(ScheduleValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                TimeSlotExceptionHandler.ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(),
                        List.of(new TimeSlotExceptionHandler.ErrorDetail(ex.getField(), ex.getIssue()))));
    }
}
