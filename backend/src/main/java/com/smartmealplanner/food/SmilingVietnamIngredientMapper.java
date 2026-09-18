package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates the conservative one-to-one Ingredient identity used for SMILING
 * Vietnam Food rows during the A3 bootstrap.
 */
final class SmilingVietnamIngredientMapper {
    private static final String FOOD_ONLY_SOURCE_CODE = "10003";
    private static final BigDecimal DEFAULT_YIELD_FACTOR = new BigDecimal("1.0000");

    private SmilingVietnamIngredientMapper() {
    }

    /**
     * Returns one explicit Ingredient mapping per SMILING source row, except
     * breast milk (source code 10003), which remains a Food-only record because
     * it is not a culinary ingredient for meal planning.
     */
    static CatalogImportIngredientMapping map(
            String sourceCode,
            String displayName,
            String categoryCode) {
        if (FOOD_ONLY_SOURCE_CODE.equals(sourceCode)) {
            return null;
        }
        return new CatalogImportIngredientMapping(
                "ING_SMILING_VN_" + sourceCode,
                displayName,
                categoryCode,
                "g",
                IngredientPreparationState.UNSPECIFIED,
                DEFAULT_YIELD_FACTOR,
                true,
                List.of());
    }
}
