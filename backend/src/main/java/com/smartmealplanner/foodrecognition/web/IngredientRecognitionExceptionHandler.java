package com.smartmealplanner.foodrecognition.web;

import com.smartmealplanner.foodrecognition.IngredientRecognitionException;
import com.smartmealplanner.foodrecognition.IngredientRecognitionFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe web mapping for YOLO recognition failures. */
@RestControllerAdvice(assignableTypes = IngredientRecognitionController.class)
@Order(-2)
public class IngredientRecognitionExceptionHandler {
    private final ApiProblems problems;

    public IngredientRecognitionExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(IngredientRecognitionException.class)
    ProblemDetail recognition(
            IngredientRecognitionException exception,
            HttpServletRequest request) {
        IngredientRecognitionFailure failure = exception.failure();
        HttpStatus status = switch (failure) {
            case INVALID_IMAGE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case IMAGE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case AI_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_SERVICE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AI_BAD_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        return problems.create(status, request, "FOOD_RECOGNITION_" + failure.name());
    }
}
