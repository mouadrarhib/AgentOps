package com.AgentOps.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Linear-time blank check, so validation never hands an attacker a backtracking
 * regex to grind on. Replaces the {@code @Pattern(regexp = ".*\S.*")} idiom,
 * which is super-linear on long whitespace-only input.
 */
public class NotBlankIfPresentValidator implements ConstraintValidator<NotBlankIfPresent, CharSequence> {

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.length();
        for (int index = 0; index < length; index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
