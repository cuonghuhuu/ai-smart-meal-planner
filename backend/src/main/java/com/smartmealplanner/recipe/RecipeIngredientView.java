package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeIngredientView(
        Integer lineNumber,
        UUID ingredientPublicId,
        String ingredientCode,
        String ingredientDisplayName,
        UUID foodPublicId,
        String foodCode,
        String foodDisplayName,
        BigDecimal quantity,
        String unitCode,
        String unitDisplayName,
        String preparationNote,
        boolean optional,
        boolean allowSubstitution,
        String sectionLabel) {
}
