package com.smartmealplanner.nutrition.web;

import java.time.LocalDate;
import java.util.List;

/** Public dated Nutrition target snapshot. */
public record NutritionTargetResponse(
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String origin,
        String calculationMethod,
        String activityLevelCode,
        String nutritionGoalCode,
        List<NutritionTargetValueResponse> nutrientValues) {
}
