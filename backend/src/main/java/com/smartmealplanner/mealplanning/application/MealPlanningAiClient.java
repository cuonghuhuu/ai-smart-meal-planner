package com.smartmealplanner.mealplanning.application;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;

/** Stateless internal transport; no persistence or user JWT is sent. */
public interface MealPlanningAiClient {
    MealPlanGenerationResponse generate(MealPlanGenerationRequest request);
}
