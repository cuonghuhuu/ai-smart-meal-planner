package com.smartmealplanner.mealplanning.web;

import com.smartmealplanner.mealplanning.MealPlanException;
import com.smartmealplanner.mealplanning.MealPlanFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(-2)
public class MealPlanExceptionHandler {
    private final ApiProblems problems;

    public MealPlanExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(MealPlanException.class)
    ProblemDetail failure(MealPlanException exception, HttpServletRequest request) {
        return problems.create(statusFor(exception.failure()), request,
                exception.failure().name());
    }

    private static HttpStatus statusFor(MealPlanFailure failure) {
        return switch (failure) {
            case INVALID_REQUEST, UNSUPPORTED_DIETARY_CONSTRAINT,
                    NO_ELIGIBLE_RECIPE -> HttpStatus.BAD_REQUEST;
            case MEAL_PLAN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_PLAN_STATE -> HttpStatus.CONFLICT;
            case CORRUPTED_MEAL_PLAN_DATA -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
