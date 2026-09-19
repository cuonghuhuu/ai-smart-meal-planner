package com.smartmealplanner.mealplanning.web;

import java.time.LocalDate;
import java.util.List;

/** Authenticated request for one deterministic meal-plan generation. */
public record MealPlanGenerationRequest(
        LocalDate startDate,
        Integer days,
        List<String> mealSlotCodes,
        Integer defaultServings,
        Integer maxMinutesPerMeal) {
}
