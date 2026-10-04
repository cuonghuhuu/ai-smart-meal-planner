package com.smartmealplanner.mealplanning.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.SecurityConfiguration;
import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService;
import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService.Detail;
import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService.Entry;
import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService.UnfilledSlot;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;
import com.smartmealplanner.shared.web.ApiExceptionHandler;
import com.smartmealplanner.shared.web.ApiProblems;
import com.smartmealplanner.shared.web.RequestIdFilter;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MealPlanQueryController.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, ApiProblems.class,
        ApiExceptionHandler.class, RequestIdFilter.class})
class MealPlanQueryControllerTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();
    private static final String PATH = "/api/v1/me/meal-plans/{mealPlanPublicId}";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);

    @Autowired MockMvc mvc;
    @MockitoBean PersistedMealPlanQueryService plans;

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        mvc.perform(get(PATH, PLAN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(plans);
    }

    @Test
    void ownerGetsPublicFieldsAndResolvedRecipe() throws Exception {
        when(plans.get(USER, PLAN)).thenReturn(new Detail(PLAN, REQUEST,
                GenerationStatus.SUCCEEDED, DATE, DATE.plusDays(6),
                new BigDecimal("2.00"),
                List.of(new Entry(DATE, MealSlotCode.BREAKFAST, RECIPE,
                        "Oatmeal with Banana", new BigDecimal("2.00"))),
                List.of()));

        String body = mvc.perform(get(PATH, PLAN)
                        .with(jwt().jwt(value -> value.subject(USER.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealPlanPublicId").value(PLAN.toString()))
                .andExpect(jsonPath("$.requestPublicId").value(REQUEST.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.startDate").value("2026-10-05"))
                .andExpect(jsonPath("$.endDate").value("2026-10-11"))
                .andExpect(jsonPath("$.defaultServings").value(2.00))
                .andExpect(jsonPath("$.entries[0].planDate").value("2026-10-05"))
                .andExpect(jsonPath("$.entries[0].mealSlotCode").value("BREAKFAST"))
                .andExpect(jsonPath("$.entries[0].recipePublicId").value(RECIPE.toString()))
                .andExpect(jsonPath("$.entries[0].recipeTitle").value("Oatmeal with Banana"))
                .andExpect(jsonPath("$.entries[0].servings").value(2.00))
                .andExpect(jsonPath("$.unfilledSlots.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\"", "userId", "recipeId",
                "mealSlotTypeId", "sourceRequestId", "internalId", "version",
                "constraintsHash", "rawRequest", "rawResponse");
    }

    @Test
    void degradedResponseIncludesUnfilledSlot() throws Exception {
        when(plans.get(USER, PLAN)).thenReturn(new Detail(PLAN, REQUEST,
                GenerationStatus.DEGRADED, DATE, DATE.plusDays(1),
                new BigDecimal("2.00"), List.of(),
                List.of(new UnfilledSlot(DATE.plusDays(1), MealSlotCode.DINNER,
                        UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE,
                        "No eligible dinner"))));

        mvc.perform(get(PATH, PLAN)
                        .with(jwt().jwt(value -> value.subject(USER.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.unfilledSlots[0].planDate").value("2026-10-06"))
                .andExpect(jsonPath("$.unfilledSlots[0].mealSlotCode").value("DINNER"))
                .andExpect(jsonPath("$.unfilledSlots[0].reasonCode").value("NO_ELIGIBLE_RECIPE"))
                .andExpect(jsonPath("$.unfilledSlots[0].explanation").value("No eligible dinner"));
    }

    @Test
    void missingPlanUsesStandardProblem() throws Exception {
        when(plans.get(USER, PLAN)).thenThrow(new EntityNotFoundException("Meal plan not found"));

        mvc.perform(get(PATH, PLAN)
                        .with(jwt().jwt(value -> value.subject(USER.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The resource was not found."));
    }
}
