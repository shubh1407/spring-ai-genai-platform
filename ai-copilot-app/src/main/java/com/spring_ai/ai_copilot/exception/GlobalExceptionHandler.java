package com.spring_ai.ai_copilot.exception;

import com.spring_ai.ai_copilot.dto.ErrorResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
       this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return buildErrorResponse("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND,
               List.of("Requested resource was not found"), "resource.not.found", e);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException e) {
       return buildErrorResponse("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
               List.of("Missing required parameter: " + e.getParameterName()), "validation.error", e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
       return buildErrorResponse("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED,
               List.of("HTTP method not allowed"), "method.not.allowed", e);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(HandlerMethodValidationException e) {
       return buildErrorResponse("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
               List.of("Request validation failed"), "validation.error", e);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
       return buildErrorResponse("INVALID_JSON", HttpStatus.BAD_REQUEST,
               List.of("Malformed JSON body"), "invalid.json", e);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(TypeMismatchException e) {
       return buildErrorResponse("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
               List.of("Invalid parameter value"), "validation.error", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
       String errorCode = "INTERNAL_SERVER_ERROR";
       HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
       recordErrorMetrics("application.error", errorCode, status);
       log.error("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
               "application.error",
               status.value(),
               errorCode,
               e.getClass().getSimpleName(),
               e);

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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

       String errorCode = "VALIDATION_ERROR";
       HttpStatus status = HttpStatus.BAD_REQUEST;
       recordErrorMetrics("validation.error", errorCode, status);
       log.warn("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
               "validation.error",
               status.value(),
               errorCode,
               ex.getClass().getSimpleName());

       List<String> messages = ex.getConstraintViolations()
               .stream()
               .map(ConstraintViolation::getMessage)
               .toList();


       ErrorResponse errorResponse = new ErrorResponse(
               LocalDateTime.now(),
               status.value(),
               errorCode,
               messages);

       return ResponseEntity
               .status(status)
               .body(errorResponse);
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderException(AiProviderException e) {
       HttpStatus status = e.getStatus();
       String errorCode = e.getErrorCode();
       recordErrorMetrics("ai.provider.error", errorCode, status);
       log.error("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
               "ai.provider.error",
               status.value(),
               errorCode,
               e.getCause() != null ? e.getCause().getClass().getSimpleName() : e.getClass().getSimpleName(),
               e);

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

    @ExceptionHandler(value = UnsupportedDocumentTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedDocumentTypeException(UnsupportedDocumentTypeException e) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                "RAG_VALIDATION_ERROR",
                List.of(e.getMessage())
        );
        recordErrorMetrics(
                "rag.document.ingestion.error",
                "RAG_VALIDATION_ERROR",
                status
        );
        return ResponseEntity
               .status(status)
               .body(response);
    }

    @ExceptionHandler(ToolValidationException.class)
    public ResponseEntity<ErrorResponse> handleToolValidationException(
            ToolValidationException e) {

        HttpStatus status = HttpStatus.BAD_REQUEST;
        String errorCode = "TOOL_VALIDATION_ERROR";
        String operation = "tool.validation";

        recordErrorMetrics(operation, errorCode, status);

        log.warn(
                "global.exception.handler.failed operation={} status={} errorCode={} exception={}",
                operation,
                status.value(),
                errorCode,
                e.getClass().getSimpleName()
        );

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

    private ResponseEntity<ErrorResponse> buildErrorResponse(String errorCode, HttpStatus status, List<String> messages, String operation, Throwable ex) {
       recordErrorMetrics(operation, errorCode, status);
       if ("VALIDATION_ERROR".equals(errorCode)) {
           log.warn("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
                   operation,
                   status.value(),
                   errorCode,
                   "framework.error");
       } else {
           if (ex != null) {
               log.error("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
                       operation,
                       status.value(),
                       errorCode,
                       ex.getClass().getSimpleName(),
                       ex);
           } else {
               log.error("global.exception.handler.failed operation={} status={} errorCode={} exception={}",
                       operation,
                       status.value(),
                       errorCode,
                       "framework.error");
           }
       }

       ErrorResponse response = new ErrorResponse(
               LocalDateTime.now(),
               status.value(),
               errorCode,
               messages
       );
       return ResponseEntity.status(status).body(response);
    }

    private void recordErrorMetrics(String operation, String errorCode, HttpStatus status) {
       meterRegistry.counter("app.errors",
               "operation", operation,
               "status", String.valueOf(status.value()),
               "errorCode", errorCode).increment();

        if ("VALIDATION_ERROR".equals(errorCode)
                || "TOOL_VALIDATION_ERROR".equals(errorCode)) {

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
