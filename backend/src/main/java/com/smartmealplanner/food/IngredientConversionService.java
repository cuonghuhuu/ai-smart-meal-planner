package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves only recorded, ingredient-specific conversion facts; it never guesses. */
@Service
public class IngredientConversionService {
    private final IngredientRepository ingredients;
    private final IngredientUnitConversionRepository conversions;

    IngredientConversionService(IngredientRepository ingredients, IngredientUnitConversionRepository conversions) {
        this.ingredients = ingredients; this.conversions = conversions;
    }

    @Transactional(readOnly = true)
    public IngredientConversionResult convert(UUID ingredientPublicId, String fromUnitCode,
            BigDecimal quantity, String toUnitCode) {
        if (ingredientPublicId == null || fromUnitCode == null || fromUnitCode.isBlank()
                || toUnitCode == null || toUnitCode.isBlank() || quantity == null || quantity.signum() <= 0) {
            throw new FoodCatalogException(FoodCatalogFailure.INVALID_REQUEST);
        }
        Ingredient ingredient = ingredients.findActiveSummaryByPublicId(CatalogIds.uuidToBytes(ingredientPublicId))
                .orElseThrow(() -> new FoodCatalogException(FoodCatalogFailure.INGREDIENT_NOT_FOUND));
        return conversions.findByIngredientAndUnitCodes(ingredient.internalId(), fromUnitCode, toUnitCode)
                .map(value -> IngredientConversionResult.available(quantity.multiply(value.toQuantity())
                        .divide(value.fromQuantity(), java.math.MathContext.DECIMAL128)))
                .orElseGet(IngredientConversionResult::unavailable);
    }
}
