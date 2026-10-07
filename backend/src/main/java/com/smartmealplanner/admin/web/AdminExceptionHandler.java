package com.smartmealplanner.admin.web;

import com.smartmealplanner.admin.AdminException;
import com.smartmealplanner.admin.AdminFailure;
import com.smartmealplanner.shared.web.ApiProblems;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(-2)
public class AdminExceptionHandler {

    private final ApiProblems problems;

    public AdminExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    @ExceptionHandler(AdminException.class)
    ProblemDetail adminFailure(
            AdminException exception,
            HttpServletRequest request) {

        return problems.create(
                statusFor(exception.failure()),
                request,
                exception.failure().name());
    }

    private static HttpStatus statusFor(AdminFailure failure) {
        return switch (failure) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_LIFECYCLE,
                    SELF_SUSPENSION_NOT_ALLOWED -> HttpStatus.CONFLICT;
            case CORRUPTED_DATA,
                    CORRUPTED_RECIPE_DATA -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
