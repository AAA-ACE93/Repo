package com.escrow.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class FutureByOneMinuteValidator implements ConstraintValidator<FutureByOneMinute, Instant> {

    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull handles null check separately
        }
        return value.isAfter(Instant.now().plus(1, ChronoUnit.MINUTES));
    }
}
