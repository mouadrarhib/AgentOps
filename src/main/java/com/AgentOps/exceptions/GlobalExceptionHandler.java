package com.AgentOps.exceptions;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ProblemDetail handleAuthentication(org.springframework.security.core.AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed.");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(org.springframework.security.access.AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR = "An unexpected error occurred. Please try again later.";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException exception) {
        return problem(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT,
                "The operation conflicts with existing data or a related resource.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT,
                "The resource was changed by another operation. Reload it and try again.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validationProblem(HttpStatus.BAD_REQUEST, errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ValidationError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationError(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")))
                .toList();
        return validationProblem(exception.getStatusCode(), errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleHandlerMethodValidationException(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) {
            return serverProblem(exception, exception.getStatusCode());
        }
        List<ValidationError> errors = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameter = Objects.requireNonNullElse(
                    result.getMethodParameter().getParameterName(), "parameter");
            result.getResolvableErrors().forEach(error -> errors.add(new ValidationError(
                    error instanceof FieldError fieldError ? fieldError.getField() : parameter,
                    Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"))));
        });
        exception.getCrossParameterValidationResults().forEach(error -> errors.add(new ValidationError(
                "request", Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"))));
        return validationProblem(exception.getStatusCode(), errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST,
                "The request body is missing or contains malformed JSON or invalid field values.");
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(TypeMismatchException exception) {
        if (exception instanceof ConversionNotSupportedException) {
            return serverProblem(exception, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return problem(HttpStatus.BAD_REQUEST,
                "A request parameter or path variable has an invalid type.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception exception, HttpServletResponse response) {
        if (exception instanceof ErrorResponse errorResponse) {
            // Returning ProblemDetail sets the status and body; preserve framework headers explicitly.
            errorResponse.getHeaders().forEach((name, values) ->
                    values.forEach(value -> response.addHeader(name, value)));
            if (errorResponse.getStatusCode().is5xxServerError()) {
                return serverProblem(exception, errorResponse.getStatusCode());
            }
            return withTimestamp(errorResponse.getBody());
        }
        return serverProblem(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ProblemDetail serverProblem(Exception exception, HttpStatusCode status) {
        LOGGER.error("API request failed", exception);
        return problem(status, INTERNAL_ERROR);
    }

    private ProblemDetail problem(HttpStatusCode status, String detail) {
        return withTimestamp(ProblemDetail.forStatusAndDetail(status, detail));
    }

    private ProblemDetail withTimestamp(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private ProblemDetail validationProblem(HttpStatusCode status, List<ValidationError> errors) {
        ProblemDetail problem = problem(status, "Request validation failed.");
        problem.setProperty("errors", errors);
        return problem;
    }
}
