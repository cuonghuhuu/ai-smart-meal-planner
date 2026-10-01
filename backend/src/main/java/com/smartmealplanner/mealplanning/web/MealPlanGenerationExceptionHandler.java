package com.smartmealplanner.mealplanning.web;

import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationException;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationFailure;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceException;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe API mapping for persisted meal-plan generation failures. */
@RestControllerAdvice(assignableTypes = MealPlanGenerationController.class)
@Order(-2)
public class MealPlanGenerationExceptionHandler {
    private final ApiProblems problems;

    public MealPlanGenerationExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(MealPlanningIntegrationException.class)
    ProblemDetail integration(MealPlanningIntegrationException exception,
            HttpServletRequest request) {
        MealPlanningIntegrationFailure failure = exception.failure();
        HttpStatus status = switch (failure) {
            case INVALID_GENERATION_INPUT -> HttpStatus.BAD_REQUEST;
            case AI_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_SERVICE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AI_BAD_RESPONSE, AI_SEARCH_BUDGET_EXHAUSTED -> HttpStatus.BAD_GATEWAY;
            case SNAPSHOT_LIMIT_EXCEEDED, SNAPSHOT_INCONSISTENT,
                    TRANSACTION_BOUNDARY_VIOLATION -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return problems.create(status, request, failure.name());
    }

    @ExceptionHandler({MealPlanPersistenceException.class, DataAccessException.class})
    ProblemDetail persistence(Exception exception, HttpServletRequest request) {
        return problems.create(HttpStatus.INTERNAL_SERVER_ERROR, request,
                "MEAL_PLAN_PERSISTENCE_FAILED");
    }
}
