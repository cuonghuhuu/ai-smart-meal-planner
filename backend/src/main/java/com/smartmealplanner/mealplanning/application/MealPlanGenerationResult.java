package com.smartmealplanner.mealplanning.application;

import java.time.Duration;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;

/** Validated internal result for Gate E; nothing has been persisted. */
public record MealPlanGenerationResult(MealPlanGenerationRequest request,
        MealPlanGenerationResponse response, Duration aiDuration) { }
