package com.smartmealplanner.shopping.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.SecurityConfiguration;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.shared.web.ApiExceptionHandler;
import com.smartmealplanner.shared.web.ApiProblems;
import com.smartmealplanner.shared.web.RequestIdFilter;
import com.smartmealplanner.shopping.application.ShoppingListQueryService;

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

@WebMvcTest(controllers = ShoppingListController.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, ApiProblems.class,
        ApiExceptionHandler.class, RequestIdFilter.class})
class ShoppingListControllerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final UUID CHICKEN = UUID.randomUUID();
    private static final UUID SALT = UUID.randomUUID();
    private static final String PATH =
            "/api/v1/me/meal-plans/{mealPlanPublicId}/shopping-list";

    @Autowired MockMvc mvc;
    @MockitoBean ShoppingListQueryService shoppingLists;

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        mvc.perform(get(PATH, PLAN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(shoppingLists);
    }

    @Test
    void ownerGetsExplainablePublicProjection() throws Exception {
        when(shoppingLists.get(USER, PLAN)).thenReturn(
                new ShoppingListQueryService.Result(
                        PLAN,
                        GenerationStatus.DEGRADED,
                        List.of(new ShoppingListQueryService.Item(
                                CHICKEN, "chicken", "Chicken",
                                new BigDecimal("800.0000"),
                                new BigDecimal("550.0000"),
                                new BigDecimal("250.0000"),
                                "g")),
                        List.of(new ShoppingListQueryService.UnquantifiedItem(
                                SALT, "salt", "Salt"))));

        String body = mvc.perform(get(PATH, PLAN)
                        .with(jwt().jwt(value -> value.subject(USER.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealPlanPublicId").value(PLAN.toString()))
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.items[0].ingredientPublicId")
                        .value(CHICKEN.toString()))
                .andExpect(jsonPath("$.items[0].ingredientCode").value("chicken"))
                .andExpect(jsonPath("$.items[0].ingredientDisplayName").value("Chicken"))
                .andExpect(jsonPath("$.items[0].requiredQuantity").value(800.0000))
                .andExpect(jsonPath("$.items[0].pantryCoveredQuantity").value(550.0000))
                .andExpect(jsonPath("$.items[0].quantityToBuy").value(250.0000))
                .andExpect(jsonPath("$.items[0].unitCode").value("g"))
                .andExpect(jsonPath("$.unquantifiedItems[0].ingredientPublicId")
                        .value(SALT.toString()))
                .andExpect(jsonPath("$.unquantifiedItems[0].ingredientDisplayName")
                        .value("Salt"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(
                "userId", "recipeId", "pantryItemId", "internalId", "version");
    }

    @Test
    void missingOrCrossOwnerPlanUsesStandardNotFoundProblem() throws Exception {
        when(shoppingLists.get(USER, PLAN))
                .thenThrow(new EntityNotFoundException("Meal plan not found"));

        mvc.perform(get(PATH, PLAN)
                        .with(jwt().jwt(value -> value.subject(USER.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The resource was not found."));
    }
}
