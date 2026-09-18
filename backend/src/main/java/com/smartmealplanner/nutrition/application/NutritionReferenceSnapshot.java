package com.smartmealplanner.nutrition.application;

/** Immutable nutrient metadata resolved for a read-only consumer. */
public record NutritionReferenceSnapshot(
        Long internalId,
        String code,
        String displayName,
        String unitCode,
        String unitDisplayName) {
}
