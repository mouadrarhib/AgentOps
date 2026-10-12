package com.AgentOps;

import com.AgentOps.dto.request.OrderRequest;
import com.AgentOps.dto.request.ConversationRequest;
import com.AgentOps.dto.request.UserRequest;
import com.AgentOps.exceptions.*;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalExceptionHandlerTests.TestController.class)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.TestController.class})
class GlobalExceptionHandlerTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void missingResourceReturnsProblemDetail() throws Exception {
        mvc.perform(get("/exception-tests/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // Spring omits the default type; RFC 9457 defines it as about:blank.
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order with ID 42 was not found."))
                .andExpect(jsonPath("$.instance").value("/exception-tests/missing"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void mapsIntentionalBusinessFailures() throws Exception {
        mvc.perform(get("/exception-tests/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The order is already cancelled."));
        mvc.perform(get("/exception-tests/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The ticket customer does not match the order."));
        mvc.perform(get("/exception-tests/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You cannot approve this action."));
    }

    @Test
    void validationIncludesFieldsWithoutSubmittedPasswords() throws Exception {
        mvc.perform(post("/exception-tests/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"secret\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password")))
                .andExpect(jsonPath("$.errors[*].message", hasItem("Email must be valid")))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(jsonPath("$.errors[*].rejectedValue").doesNotExist());
    }

    @Test
    void validationPreservesMultipleFailuresOnTheSameField() throws Exception {
        mvc.perform(post("/exception-tests/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.test\",\"password\":\"\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", everyItem(is("password"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"customerId\":1,\"status\":\"UNKNOWN\"}", ""})
    void malformedBodiesAndInvalidEnumsReturnBadRequest(String body) throws Exception {
        mvc.perform(post("/exception-tests/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "The request body is missing or contains malformed JSON or invalid field values."));
    }

    @Test
    void validatesControllerParameters() throws Exception {
        mvc.perform(get("/exception-tests/parameter").param("id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("ID must be positive"));
        mvc.perform(get("/exception-tests/id/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "A request parameter or path variable has an invalid type."));
    }

    @Test
    void preservesFrameworkStatusesAndHeaders() throws Exception {
        mvc.perform(delete("/exception-tests/orders"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/exception-tests/orders").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        mvc.perform(get("/exception-tests/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/exception-tests/unknown-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/exception-tests/status"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "concurrent"})
    void databaseConflictsHideInternalDetails(String path) throws Exception {
        mvc.perform(get("/exception-tests/" + path))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("internal-secret"))));
    }

    @Test
    void unexpectedFailuresReturnSafeInternalServerError() throws Exception {
        mvc.perform(get("/exception-tests/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.instance").value("/exception-tests/failure"))
                .andExpect(content().string(not(containsString("internal-secret"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void constraintViolationsIncludeSafeFieldErrors() throws Exception {
        mvc.perform(get("/exception-tests/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("userId"))
                .andExpect(jsonPath("$.errors[0].message").value("userId must be positive"));
    }

    @Test
    void invalidControllerReturnValuesRemainServerErrors() throws Exception {
        mvc.perform(get("/exception-tests/invalid-return"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @RestController
    @RequestMapping("/exception-tests")
    static class TestController {
        @Autowired
        private Validator validator;

        @GetMapping("/missing")
        void missing() { throw new ResourceNotFoundException("Order", 42L); }

        @GetMapping("/conflict")
        void conflict() { throw new ConflictException("The order is already cancelled."); }

        @GetMapping("/bad-request")
        void badRequest() { throw new BadRequestException("The ticket customer does not match the order."); }

        @GetMapping("/forbidden")
        void forbidden() { throw new ForbiddenException("You cannot approve this action."); }

        @PostMapping("/users")
        void user(@Valid @RequestBody UserRequest request) { }

        @PostMapping("/orders")
        void order(@Valid @RequestBody OrderRequest request) { }

        @GetMapping("/parameter")
        void parameter(@RequestParam @Positive(message = "ID must be positive") Long id) { }

        @GetMapping("/id/{id}")
        void id(@PathVariable Long id) { }

        @GetMapping("/database")
        void database() { throw new DataIntegrityViolationException("internal-secret SQL"); }

        @GetMapping("/concurrent")
        void concurrent() { throw new OptimisticLockingFailureException("internal-secret SQL"); }

        @GetMapping("/failure")
        void failure() { throw new IllegalStateException("internal-secret"); }

        @GetMapping("/status")
        void status() { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later"); }

        @GetMapping("/constraint")
        void constraint() {
            throw new ConstraintViolationException(validator.validate(new ConversationRequest(0L)));
        }

        @GetMapping("/invalid-return")
        @Positive
        Long invalidReturn() { return -1L; }
    }
}
