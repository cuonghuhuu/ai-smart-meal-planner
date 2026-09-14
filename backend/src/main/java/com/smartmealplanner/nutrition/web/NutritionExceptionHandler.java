package com.smartmealplanner.nutrition.web;

import com.smartmealplanner.nutrition.application.NutritionApplicationException;
import com.smartmealplanner.nutrition.application.NutritionApplicationFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Nutrition-owned mapping of structured application failures to Problem Details. */
@RestControllerAdvice
@Order(-2)
public class NutritionExceptionHandler {

    private final ApiProblems problems;

    public NutritionExceptionHandler(
            ApiProblems problems) {

        this.problems = problems;
    }

    @ExceptionHandler(NutritionApplicationException.class)
    ProblemDetail nutritionFailure(
            NutritionApplicationException exception,
            HttpServletRequest request) {

        return problems.create(
                statusFor(exception.failure()),
                request,
                exception.failure().name());
    }

    private static HttpStatus statusFor(
            NutritionApplicationFailure failure) {

        return switch (failure) {
            case INVALID_REQUEST,
                    UNKNOWN_NUTRIENT_CODE,
                    DUPLICATE_NUTRIENT_CODE,
                    INVALID_TARGET_VALUE -> HttpStatus.BAD_REQUEST;
            case PROFILE_NOT_FOUND,
                    NO_CURRENT_TARGET -> HttpStatus.NOT_FOUND;
            case SAME_EFFECTIVE_DATE -> HttpStatus.CONFLICT;
            case MISSING_CALCULATION_INPUT,
                    MEASUREMENT_NOT_FOUND,
                    CALCULATION_NOT_SUPPORTED,
                    GOAL_ADJUSTMENT_NOT_SUPPORTED,
                    INVALID_TIME_ZONE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CORRUPTED_TARGET_TIMELINE,
                    CORRUPTED_TARGET_DATA,
                    PERSISTENCE_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
