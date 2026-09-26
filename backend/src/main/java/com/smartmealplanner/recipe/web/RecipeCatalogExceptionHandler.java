package com.smartmealplanner.recipe.web;

import com.smartmealplanner.recipe.RecipeException;
import com.smartmealplanner.recipe.RecipeFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe HTTP mapping for Recipe catalog failures. */
@RestControllerAdvice
@Order(-2)
public class RecipeCatalogExceptionHandler {

    private final ApiProblems problems;

    public RecipeCatalogExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(RecipeException.class)
    ProblemDetail recipeFailure(
            RecipeException exception,
            HttpServletRequest request) {

        return problems.create(
                statusFor(exception.failure()),
                request,
                exception.failure().name());
    }

    private static HttpStatus statusFor(RecipeFailure failure) {
        return switch (failure) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case RECIPE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CORRUPTED_RECIPE_DATA -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
