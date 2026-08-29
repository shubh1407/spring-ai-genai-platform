package com.spring_ai.ai_copilot.exception;

import com.spring_ai.ai_copilot.dto.ErrorResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
       this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
       String errorCode = "INTERNAL_SERVER_ERROR";
       HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
       recordErrorMetrics("application.error", errorCode, status);
       log.warn("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
               "application.error",
               status.value(),
               errorCode,
               e.getClass().getSimpleName());

       ErrorResponse response = new ErrorResponse(
               LocalDateTime.now(),
               status.value(),
               errorCode,
               List.of("An unexpected error occurred")
       );

       return ResponseEntity
               .status(status)
               .body(response);
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderException(AiProviderException e) {
       HttpStatus status = e.getStatus();
       String errorCode = e.getErrorCode();
       recordErrorMetrics("ai.provider.error", errorCode, status);
       log.warn("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
               "ai.provider.error",
               status.value(),
               errorCode,
               e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName());

       ErrorResponse response = new ErrorResponse(
               LocalDateTime.now(),
               status.value(),
               errorCode,
               List.of(e.getMessage())
       );

       return ResponseEntity
               .status(status)
               .body(response);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
       String errorCode = "VALIDATION_ERROR";
       HttpStatus status = HttpStatus.BAD_REQUEST;
       recordErrorMetrics("validation.error", errorCode, status);
       log.warn("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
               "validation.error",
               status.value(),
               errorCode,
               e.getClass().getSimpleName());

       List<String> messages = e.getBindingResult().getFieldErrors().stream()
               .map(error -> error.getField() + ": " + error.getDefaultMessage())
               .toList();
       ErrorResponse errorResponse = new ErrorResponse(
               LocalDateTime.now(),
               status.value(),
               errorCode,
               messages);
       return ResponseEntity.status(status).body(errorResponse);
    }

    private void recordErrorMetrics(String operation, String errorCode, HttpStatus status) {
       meterRegistry.counter("app.errors",
               "operation", operation,
               "status", String.valueOf(status.value()),
               "errorCode", errorCode).increment();

       if ("VALIDATION_ERROR".equals(errorCode)) {
           meterRegistry.counter("app.validation.errors",
                   "operation", operation,
                   "status", String.valueOf(status.value()),
                   "errorCode", errorCode).increment();
       } else if (errorCode != null && errorCode.startsWith("AI_")) {
           meterRegistry.counter("app.ai.provider.errors",
                   "operation", operation,
                   "status", String.valueOf(status.value()),
                   "errorCode", errorCode).increment();
       }
    }
}
