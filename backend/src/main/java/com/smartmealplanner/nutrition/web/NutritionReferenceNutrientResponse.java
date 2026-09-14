package com.smartmealplanner.nutrition.web;

/** Public Nutrition nutrient reference item without surrogate identifiers. */
public record NutritionReferenceNutrientResponse(
        String code,
        String displayName,
        String nutrientKind,
        boolean core,
        Short displayOrder,
        String unitCode,
        String unitDisplayName) {
}
