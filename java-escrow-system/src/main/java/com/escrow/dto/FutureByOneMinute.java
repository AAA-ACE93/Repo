package com.escrow.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = FutureByOneMinuteValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface FutureByOneMinute {
    String message() default "Deadline must be at least 1 minute in the future";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
