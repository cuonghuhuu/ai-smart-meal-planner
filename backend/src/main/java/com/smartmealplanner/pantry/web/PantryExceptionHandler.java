package com.smartmealplanner.pantry.web;

import com.smartmealplanner.pantry.PantryException;
import com.smartmealplanner.pantry.PantryFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe HTTP mapping for Pantry-owned failures. */
@RestControllerAdvice
@Order(-2)
public class PantryExceptionHandler {

    private final ApiProblems problems;

    public PantryExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(PantryException.class)
    ProblemDetail pantryFailure(
            PantryException exception,
            HttpServletRequest request) {

        return problems.create(
                statusFor(exception.failure()),
                request,
                exception.failure().name());
    }

    private static HttpStatus statusFor(PantryFailure failure) {
        return switch (failure) {
            case INVALID_REQUEST,
                    FOOD_NOT_FOUND,
                    FOOD_NOT_MAPPED,
                    INGREDIENT_NOT_FOUND,
                    UNIT_NOT_FOUND -> HttpStatus.BAD_REQUEST;
            case PANTRY_ITEM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ITEM_NOT_OPEN -> HttpStatus.CONFLICT;
            case CORRUPTED_PANTRY_DATA -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
