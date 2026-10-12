package com.AgentOps;

import com.AgentOps.dto.request.UserRequest;
import com.AgentOps.exceptions.GlobalExceptionHandler;
import com.AgentOps.exceptions.ValidationError;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.core.MethodParameter;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerUnitTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletResponse response;

    @Mock
    private MethodArgumentNotValidException bodyValidationException;

    @Mock
    private HandlerMethodValidationException methodValidationException;

    @Mock
    private ParameterValidationResult parameterValidationResult;

    @Test
    void preservesFrameworkProblemTypeAndEveryHeaderValue() {
        ErrorResponseException exception = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        exception.getBody().setType(URI.create("/problems/rate-limit"));
        exception.getBody().setDetail("Too many requests.");
        exception.getHeaders().add("Retry-After", "30");
        exception.getHeaders().add("Vary", "Accept");
        exception.getHeaders().add("Vary", "Origin");

        ProblemDetail problem = handler.handleException(exception, response);

        assertSame(exception.getBody(), problem);
        assertEquals(429, problem.getStatus());
        assertEquals(URI.create("/problems/rate-limit"), problem.getType());
        assertEquals("Too many requests.", problem.getDetail());
        assertInstanceOf(Instant.class, problem.getProperties().get("timestamp"));
        verify(response).addHeader("Retry-After", "30");
        verify(response).addHeader("Vary", "Accept");
        verify(response).addHeader("Vary", "Origin");
        verifyNoMoreInteractions(response);
    }

    @Test
    void redactsFrameworkServerErrorDetailsWhilePreservingStatus() {
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "internal database credentials");

        ProblemDetail problem = handler.handleException(exception, response);

        assertEquals(503, problem.getStatus());
        assertEquals("An unexpected error occurred. Please try again later.", problem.getDetail());
        assertNotSame(exception.getBody(), problem);
        verifyNoInteractions(response);
    }

    @Test
    void convertsBindingErrorsWithoutExposingRejectedValues() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new UserRequest(), "user");
        bindingResult.addError(new FieldError("user", "password", "private-password", false,
                null, null, "Password is required"));
        bindingResult.addError(new FieldError("user", "password", "private-password", false,
                null, null, "Password must contain 8 to 128 characters"));
        bindingResult.addError(new ObjectError("user", (String) null));
        when(bodyValidationException.getBindingResult()).thenReturn(bindingResult);
        when(bodyValidationException.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);

        ProblemDetail problem = handler.handleMethodArgumentNotValid(bodyValidationException);

        assertEquals(400, problem.getStatus());
        assertEquals(List.of(
                new ValidationError("password", "Password is required"),
                new ValidationError("password", "Password must contain 8 to 128 characters"),
                new ValidationError("user", "Invalid value")), problem.getProperties().get("errors"));
        assertFalse(problem.toString().contains("private-password"));
    }

    @Test
    void includesParameterAndCrossParameterValidationFailures() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerUnitTests.class.getDeclaredMethod("request", Long.class), 0);
        parameter.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
        when(methodValidationException.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(methodValidationException.getParameterValidationResults()).thenReturn(List.of(parameterValidationResult));
        when(parameterValidationResult.getMethodParameter()).thenReturn(parameter);
        when(parameterValidationResult.getResolvableErrors()).thenReturn(List.of(
                new DefaultMessageSourceResolvable(new String[]{"Positive"}, null, "ID must be positive")));
        when(methodValidationException.getCrossParameterValidationResults()).thenReturn(List.of(
                new DefaultMessageSourceResolvable(new String[]{"Consistent"}, null, "Parameters do not match")));

        ProblemDetail problem = handler.handleHandlerMethodValidationException(methodValidationException);

        assertEquals(400, problem.getStatus());
        assertEquals(List.of(new ValidationError("id", "ID must be positive"),
                new ValidationError("request", "Parameters do not match")), problem.getProperties().get("errors"));
    }

    @Test
    void treatsInvalidReturnValuesAsServerFailuresWithoutExposingValidationDetails() {
        when(methodValidationException.isForReturnValue()).thenReturn(true);
        when(methodValidationException.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);

        ProblemDetail problem = handler.handleHandlerMethodValidationException(methodValidationException);

        assertEquals(500, problem.getStatus());
        assertEquals("An unexpected error occurred. Please try again later.", problem.getDetail());
        assertFalse(problem.getProperties().containsKey("errors"));
        verify(methodValidationException, never()).getParameterValidationResults();
    }

    @Test
    void treatsUnsupportedServerConversionsAsInternalErrors() {
        ConversionNotSupportedException exception = new ConversionNotSupportedException(
                "private-value", Long.class, new IllegalStateException("No converter configured"));

        ProblemDetail problem = handler.handleTypeMismatch(exception);

        assertEquals(500, problem.getStatus());
        assertEquals("An unexpected error occurred. Please try again later.", problem.getDetail());
        assertFalse(problem.toString().contains("private-value"));
    }

//     private void request(Long id) {
//         // Provides a real method parameter for framework validation results.
//     }
}
