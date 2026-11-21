package com.AgentOps.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class NotBlankIfPresentValidatorTests {

    private final NotBlankIfPresentValidator validator = new NotBlankIfPresentValidator();

    @Mock
    private ConstraintValidatorContext context;

    @Test
    void acceptsNullBecauseTheFieldIsOptional() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void rejectsEmptyString() {
        assertFalse(validator.isValid("", context));
    }

    @Test
    void rejectsSingleSpace() {
        assertFalse(validator.isValid(" ", context));
    }

    @Test
    void rejectsTabsNewlinesAndCarriageReturns() {
        assertFalse(validator.isValid("\t\n\r", context));
    }

    @Test
    void rejectsLongWhitespaceOnlyString() {
        assertFalse(validator.isValid(" ".repeat(10_000), context));
    }

    @Test
    void acceptsSingleNonWhitespaceCharacter() {
        assertTrue(validator.isValid("a", context));
    }

    @Test
    void acceptsTextPaddedWithSurroundingWhitespace() {
        assertTrue(validator.isValid("  hello world  ", context));
    }

    @Test
    void acceptsTextStartingWithWhitespace() {
        assertTrue(validator.isValid(" lead", context));
    }

    @Test
    void acceptsTextEndingWithWhitespace() {
        assertTrue(validator.isValid("trail\t", context));
    }

    @Test
    void supportsNonStringCharSequence() {
        assertTrue(validator.isValid(new StringBuilder("value"), context));
        assertFalse(validator.isValid(new StringBuilder("   "), context));
    }
}
