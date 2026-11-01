package com.company.leadershipintelligence.api.exception;

import com.company.leadershipintelligence.api.controller.ApiRequestException;
import com.company.leadershipintelligence.api.response.ApiError;
import com.company.leadershipintelligence.config.beans.CorrelationIdFilter;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.TreeMap;

/**
 * Turns every failure into the stable error body (specv3.md Sections 28.10, 29). Messages are written here or by the
 * domain and contain no stack traces, configuration internals or raw answers. Unexpected failures are answered with a
 * generic message; only the exception class and the correlation id are logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ScoringException.class)
    ResponseEntity<ApiError> scoring(ScoringException e) {
        HttpStatus status = ErrorStatus.of(e.code());
        if (ErrorStatus.isServerFault(e.code())) {
            LOG.error("scoring request failed code={} correlationId={}", e.code(), correlationId());
            return build(status, e.code().name(), "The request could not be completed.", Map.of());
        }
        return build(status, e.code().name(), e.getMessage(), e.details());
    }

    @ExceptionHandler(ApiRequestException.class)
    ResponseEntity<ApiError> badRequest(ApiRequestException e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is not valid.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof UnrecognizedPropertyException unknown) {
            return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request contains a field that is not allowed.",
                    Map.of("field", String.valueOf(unknown.getPropertyName())));
        }
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request body is missing or malformed.", Map.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class})
    ResponseEntity<ApiError> parameter(Exception e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "A request parameter is missing or malformed.", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_REQUEST", "The content type is not supported; use application/json.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "INVALID_REQUEST", "The method is not allowed for this resource.", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return build(HttpStatus.NOT_FOUND, "INVALID_REQUEST", "No such resource.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "The caller's role does not allow this operation.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        LOG.error("unexpected failure type={} correlationId={}", e.getClass().getName(), correlationId());
        LOG.debug("unexpected failure detail", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ScoringErrorCode.SCORING_EXECUTION_FAILED.name(),
                "The request could not be completed.", Map.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message, Map<String, String> details) {
        return ResponseEntity.status(status).body(new ApiError(code, message, details, correlationId()));
    }

    private static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
