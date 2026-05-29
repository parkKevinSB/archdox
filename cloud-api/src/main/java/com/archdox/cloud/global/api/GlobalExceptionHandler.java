package com.archdox.cloud.global.api;

import com.archdox.cloud.inspection.application.ReportSubmitValidationException;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ReportSubmitValidationException.class)
    ResponseEntity<ReportSubmitValidationResponse> handleReportSubmitValidation(ReportSubmitValidationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.validation());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> ApiFieldError.of(error.getField(), error.getCode(), error.getDefaultMessage()))
                .toList();
        var message = fieldErrors.stream()
                .findFirst()
                .map(error -> error.field() + " " + error.message())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "errors.validation.failed",
                message,
                null,
                fieldErrors));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, ApiException ex) {
        return ResponseEntity.status(status).body(ApiError.of(
                status.value(),
                ex.code(),
                ex.messageKey(),
                ((RuntimeException) ex).getMessage(),
                ex.params(),
                List.of()));
    }
}
