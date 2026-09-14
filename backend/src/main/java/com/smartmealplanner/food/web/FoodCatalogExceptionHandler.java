package com.smartmealplanner.food.web;

import com.smartmealplanner.food.FoodCatalogException;
import com.smartmealplanner.food.FoodCatalogFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Catalog-owned safe HTTP mapping, ordered before the generic API advice. */
@RestControllerAdvice
@Order(-2)
public class FoodCatalogExceptionHandler {
    private final ApiProblems problems;
    FoodCatalogExceptionHandler(ApiProblems problems) { this.problems = problems; }
    @ExceptionHandler(FoodCatalogException.class)
    ProblemDetail catalogFailure(FoodCatalogException exception, HttpServletRequest request) {
        return problems.create(statusFor(exception.failure()), request, exception.failure().name());
    }
    private static HttpStatus statusFor(FoodCatalogFailure failure) {
        return switch (failure) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case FOOD_NOT_FOUND, INGREDIENT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INACTIVE_INGREDIENT, CONVERSION_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CORRUPTED_CATALOG_DATA -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
