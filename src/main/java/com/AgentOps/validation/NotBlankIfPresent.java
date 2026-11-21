package com.AgentOps.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Accepts {@code null} but rejects values that are empty or contain only whitespace.
 * The optional-field counterpart to {@code @NotBlank}: an omitted value is valid,
 * a supplied blank value is not.
 */
@Documented
@Constraint(validatedBy = NotBlankIfPresentValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlankIfPresent {
    String message() default "must not be blank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
