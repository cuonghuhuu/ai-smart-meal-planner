package com.smartmealplanner.mealplanning.web;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.orchestration.PersistedMealPlanGenerationService.Completed;

/** Public terminal outcome; an infeasible request has no plan. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MealPlanGenerationResponse(UUID requestPublicId,
        GenerationStatus status, UUID mealPlanPublicId) {
    static MealPlanGenerationResponse from(Completed completed) {
        return new MealPlanGenerationResponse(completed.requestPublicId(),
                completed.status(), completed.mealPlanPublicId());
    }
}
