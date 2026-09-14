package com.smartmealplanner.nutrition.application;

import com.smartmealplanner.nutrition.persistence.NutrientKind;

/**
 * Public-safe application view of a Nutrition nutrient reference.
 */
public record NutritionReferenceNutrientView(
        String code,
        String displayName,
        NutrientKind nutrientKind,
        boolean core,
        Short displayOrder,
        String unitCode,
        String unitDisplayName) {
}
