package com.smartmealplanner.nutrition.web;

import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartmealplanner.nutrition.application.NutritionApplicationException;
import com.smartmealplanner.nutrition.application.NutritionApplicationFailure;
import com.smartmealplanner.shared.web.ApiProblems;
import com.smartmealplanner.shared.web.RequestIdFilter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionExceptionHandlerTest {

    private static final String REQUEST_ID =
            "7e8ca04c-82b2-4ae3-872c-043cfc6620cf";

    @ParameterizedTest
    @MethodSource("failureMappings")
    void mapsStructuredFailuresToSafeDomainProblemDetails(
            NutritionApplicationFailure failure,
            HttpStatus expectedStatus) {

        NutritionExceptionHandler handler = new NutritionExceptionHandler(
                new ApiProblems(new ObjectMapper()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST_ID);

        ProblemDetail problem = handler.nutritionFailure(
                new NutritionApplicationException(
                        failure,
                        "private diagnostic that must not be exposed"),
                request);

        assertThat(problem.getStatus()).isEqualTo(expectedStatus.value());
        assertThat(problem.getProperties())
                .containsEntry("code", failure.name())
                .containsEntry("requestId", REQUEST_ID);
        assertThat(problem.getDetail())
                .doesNotContain("private diagnostic");
    }

    private static Stream<Arguments> failureMappings() {

        return Stream.of(
                Arguments.of(
                        NutritionApplicationFailure.INVALID_REQUEST,
                        HttpStatus.BAD_REQUEST),
                Arguments.of(
                        NutritionApplicationFailure.UNKNOWN_NUTRIENT_CODE,
                        HttpStatus.BAD_REQUEST),
                Arguments.of(
                        NutritionApplicationFailure.DUPLICATE_NUTRIENT_CODE,
                        HttpStatus.BAD_REQUEST),
                Arguments.of(
                        NutritionApplicationFailure.INVALID_TARGET_VALUE,
                        HttpStatus.BAD_REQUEST),
                Arguments.of(
                        NutritionApplicationFailure.PROFILE_NOT_FOUND,
                        HttpStatus.NOT_FOUND),
                Arguments.of(
                        NutritionApplicationFailure.NO_CURRENT_TARGET,
                        HttpStatus.NOT_FOUND),
                Arguments.of(
                        NutritionApplicationFailure.SAME_EFFECTIVE_DATE,
                        HttpStatus.CONFLICT),
                Arguments.of(
                        NutritionApplicationFailure.MISSING_CALCULATION_INPUT,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(
                        NutritionApplicationFailure.MEASUREMENT_NOT_FOUND,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(
                        NutritionApplicationFailure.CALCULATION_NOT_SUPPORTED,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(
                        NutritionApplicationFailure.GOAL_ADJUSTMENT_NOT_SUPPORTED,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(
                        NutritionApplicationFailure.INVALID_TIME_ZONE,
                        HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(
                        NutritionApplicationFailure.CORRUPTED_TARGET_TIMELINE,
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of(
                        NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                        HttpStatus.INTERNAL_SERVER_ERROR),
                Arguments.of(
                        NutritionApplicationFailure.PERSISTENCE_FAILURE,
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
