package com.smartmealplanner.nutrition.web;

import java.time.LocalDate;

/** Public result for creating a Nutrition target. */
public record NutritionTargetWriteResponse(
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String origin,
        String calculationMethod,
        String calculationStatus,
        String calculationReason) {
}
