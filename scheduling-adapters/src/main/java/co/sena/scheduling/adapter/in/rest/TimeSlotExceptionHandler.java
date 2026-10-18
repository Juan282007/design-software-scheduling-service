package co.sena.scheduling.adapter.in.rest;

import java.time.format.DateTimeParseException;
import java.util.List;
import co.sena.scheduling.domain.exception.TimeSlotInUseException;
import co.sena.scheduling.domain.exception.TimeSlotNotFoundException;
import co.sena.scheduling.domain.exception.TimeSlotValidationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce errores de dominio de time_slot al envelope HTTP común (_shared.yaml#Error) con trace_id. */
@RestControllerAdvice(assignableTypes = TimeSlotController.class)
public class TimeSlotExceptionHandler {

    @ExceptionHandler(TimeSlotValidationException.class)
    ResponseEntity<ErrorResponse> handleValidation(TimeSlotValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of("VALIDATION_ERROR", exception.getMessage(),
                List.of(new ErrorDetail(exception.getField(), exception.getIssue()))));
    }

    /** 400 del contrato: payload malformado o validación de esquema (Bean Validation generada). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleSchemaValidation(MethodArgumentNotValidException exception) {
        List<ErrorDetail> details = exception.getBindingResult().getFieldErrors().stream()
            .map(this::toDetail).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of("VALIDATION_ERROR", "Payload inválido.", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of("VALIDATION_ERROR", "Payload inválido o malformado.", List.of()));
    }

    @ExceptionHandler(DateTimeParseException.class)
    ResponseEntity<ErrorResponse> handleBadTime(DateTimeParseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of("VALIDATION_ERROR", "Hora con formato inválido (HH:mm).", List.of()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException exception) {
        List<ErrorDetail> details = exception.getConstraintViolations().stream()
            .map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage())).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse.of("VALIDATION_ERROR", "Parámetros inválidos.", details));
    }

    private ErrorDetail toDetail(FieldError error) {
        return new ErrorDetail(error.getField(), error.getDefaultMessage());
    }

    @ExceptionHandler(TimeSlotNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(TimeSlotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("TIME_SLOT_NOT_FOUND", exception.getMessage(), List.of()));
    }

    @ExceptionHandler(TimeSlotInUseException.class)
    ResponseEntity<ErrorResponse> handleInUse(TimeSlotInUseException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("TIME_SLOT_IN_USE", exception.getMessage(), List.of()));
    }

    public record ErrorResponse(ErrorBody error) {
        static ErrorResponse of(String code, String message, List<ErrorDetail> details) {
            return new ErrorResponse(ErrorBody.of(code, message, details));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, List<ErrorDetail> details, String trace_id) {
        static ErrorBody of(String code, String message, List<ErrorDetail> details) {
            return new ErrorBody(code, message, details, TraceIds.currentOrUnavailable());
        }
    }

    public record ErrorDetail(String field, String issue) {
    }
}
