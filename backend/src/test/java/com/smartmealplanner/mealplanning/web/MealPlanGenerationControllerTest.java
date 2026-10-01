package com.smartmealplanner.mealplanning.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.SecurityConfiguration;
import com.smartmealplanner.mealplanning.application.MealPlanGenerationCommand;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationException;
import com.smartmealplanner.mealplanning.application.MealPlanningIntegrationFailure;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.orchestration.PersistedMealPlanGenerationService;
import com.smartmealplanner.mealplanning.orchestration.PersistedMealPlanGenerationService.Completed;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceException;
import com.smartmealplanner.shared.web.ApiExceptionHandler;
import com.smartmealplanner.shared.web.ApiProblems;
import com.smartmealplanner.shared.web.RequestIdFilter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MealPlanGenerationController.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, ApiProblems.class, ApiExceptionHandler.class,
        MealPlanGenerationExceptionHandler.class, RequestIdFilter.class})
class MealPlanGenerationControllerTest {
    private static final String PATH = "/api/v1/me/meal-plans/generate";
    private static final UUID USER = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final String VALID = """
            {"startDate":"2026-10-01","days":2,
             "requestedMealSlots":["BREAKFAST","DINNER"],
             "defaultServings":2.50,"maxMinutesPerMeal":45}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean PersistedMealPlanGenerationService generation;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(generation);
    }

    @Test
    void successUsesPrincipalAndMapsCommandAndPublicIdentifiers() throws Exception {
        when(generation.generate(eq(USER), any(MealPlanGenerationCommand.class)))
                .thenReturn(new Completed(REQUEST, GenerationStatus.SUCCEEDED, PLAN));
        String body = mvc.perform(authenticated(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestPublicId").value(REQUEST.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.mealPlanPublicId").value(PLAN.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("mealPlanId", "userId", "internalId",
                "sourceRequestId", "rawRequest", "rawResponse");
        ArgumentCaptor<MealPlanGenerationCommand> command =
                ArgumentCaptor.forClass(MealPlanGenerationCommand.class);
        verify(generation).generate(eq(USER), command.capture());
        assertThat(command.getValue()).isEqualTo(new MealPlanGenerationCommand(
                LocalDate.of(2026, 10, 1), 2,
                List.of(MealSlotCode.BREAKFAST, MealSlotCode.DINNER),
                new BigDecimal("2.50"), 45));
    }

    @Test
    void infeasibleHasNoPlanIdentifier() throws Exception {
        when(generation.generate(eq(USER), any(MealPlanGenerationCommand.class)))
                .thenReturn(new Completed(REQUEST, GenerationStatus.INFEASIBLE, null));
        mvc.perform(authenticated(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestPublicId").value(REQUEST.toString()))
                .andExpect(jsonPath("$.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.mealPlanPublicId").doesNotExist());
    }

    @Test
    void missingRequiredFieldUsesStandardValidationProblem() throws Exception {
        mvc.perform(authenticated("{\"days\":2,\"requestedMealSlots\":[\"DINNER\"],\"defaultServings\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("The request is invalid."));
        verifyNoInteractions(generation);
    }

    @Test
    void invalidCommandUsesExistingInputCategory() throws Exception {
        when(generation.generate(eq(USER), any(MealPlanGenerationCommand.class)))
                .thenThrow(new MealPlanningIntegrationException(
                        MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT));
        mvc.perform(authenticated(VALID.replace("\"days\":2", "\"days\":8")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GENERATION_INPUT"));
    }

    @Test
    void aiFailuresUseSafeStandardProblem() throws Exception {
        for (var failure : List.of(MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE,
                MealPlanningIntegrationFailure.AI_SERVICE_TIMEOUT,
                MealPlanningIntegrationFailure.AI_BAD_RESPONSE)) {
            doThrow(new MealPlanningIntegrationException(failure,
                            new IllegalStateException("private Python detail")))
                    .when(generation).generate(eq(USER), any(MealPlanGenerationCommand.class));
            String body = mvc.perform(authenticated(VALID))
                    .andExpect(status().is(switch (failure) {
                        case AI_SERVICE_UNAVAILABLE -> 503;
                        case AI_SERVICE_TIMEOUT -> 504;
                        default -> 502;
                    }))
                    .andExpect(jsonPath("$.code").value(failure.name()))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("private Python detail");
        }
    }

    @Test
    void authorizationFailureUsesStandardForbiddenProblem() throws Exception {
        when(generation.generate(eq(USER), any(MealPlanGenerationCommand.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException(
                        "private authorization detail"));
        String body = mvc.perform(authenticated(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private authorization detail");
    }

    @Test
    void persistenceFailureIsSafe() throws Exception {
        when(generation.generate(eq(USER), any(MealPlanGenerationCommand.class)))
                .thenThrow(new MealPlanPersistenceException(
                        MealPlanPersistenceException.Reason.MISSING_REFERENCE));
        mvc.perform(authenticated(VALID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MEAL_PLAN_PERSISTENCE_FAILED"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    authenticated(String json) {
        return post(PATH).with(jwt().jwt(value -> value.subject(USER.toString())))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
