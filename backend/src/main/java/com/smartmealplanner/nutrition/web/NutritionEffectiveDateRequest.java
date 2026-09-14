package com.smartmealplanner.nutrition.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Explicit effective date supplied for a Nutrition calculation workflow. */
public record NutritionEffectiveDateRequest(
        @NotNull
        LocalDate effectiveFrom) {
}
