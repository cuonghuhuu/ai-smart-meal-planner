package com.smartmealplanner.recipe;

import java.math.BigDecimal;

/** One normalized Recipe ingredient line identified by canonical code. */
public record RecipeImportIngredient(
        Integer lineNumber,
        String ingredientCode,
        BigDecimal quantity,
        String unitCode,
        String preparationNote,
        boolean optional,
        boolean allowSubstitution,
        String sectionLabel) {
}
