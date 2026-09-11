package com.smartmealplanner.shared.web;

import com.smartmealplanner.auth.application.EmailAlreadyRegisteredException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(-1)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private final ApiProblems problems;

    public ApiExceptionHandler(ApiProblems problems) {
        this.problems = problems;
    }

    /** Keep Spring's request-error status and headers while replacing diagnostic details. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        HttpStatus status = HttpStatus.resolve(statusCode.value());

        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return new ResponseEntity<>(
                problems.create(
                        status,
                        ((ServletWebRequest) request).getRequest()),
                headers,
                status);
    }

    @ExceptionHandler({
            InvalidRequestException.class,
            ConstraintViolationException.class
    })
    ProblemDetail invalid(
            Exception exception,
            HttpServletRequest request) {

        return problems.create(
                HttpStatus.BAD_REQUEST,
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated(
            Exception exception,
            HttpServletRequest request) {

        return problems.create(
                HttpStatus.UNAUTHORIZED,
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(
            Exception exception,
            HttpServletRequest request) {

        return problems.create(
                HttpStatus.FORBIDDEN,
                request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail missing(
            Exception exception,
            HttpServletRequest request) {

        return problems.create(
                HttpStatus.NOT_FOUND,
                request);
    }

    @ExceptionHandler({
            EmailAlreadyRegisteredException.class,
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    ProblemDetail conflict(
            Exception exception,
            HttpServletRequest request) {

        return problems.create(
                HttpStatus.CONFLICT,
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(
            Exception exception,
            HttpServletRequest request) {

        // Do not log exception messages:
        // JDBC/provider messages can contain personal data or SQL.
        return problems.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                request);
    }
}