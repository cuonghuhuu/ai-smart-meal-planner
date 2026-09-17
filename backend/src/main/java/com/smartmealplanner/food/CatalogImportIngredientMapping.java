package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Explicit curator mapping from one imported Food to one canonical Ingredient. */
public record CatalogImportIngredientMapping(
        String ingredientCode,
        String displayName,
        String categoryCode,
        String defaultUnitCode,
        IngredientPreparationState preparationState,
        BigDecimal yieldFactor,
        boolean primary,
        List<CatalogImportAlias> aliases) {

    public CatalogImportIngredientMapping {
        ingredientCode = requireText(ingredientCode, 80, "ingredientCode");
        displayName = requireText(displayName, 150, "displayName");
        categoryCode = optionalText(categoryCode, 60, "categoryCode");
        categoryCode = categoryCode == null ? null : categoryCode.toUpperCase(Locale.ROOT);
        defaultUnitCode = optionalText(defaultUnitCode, 20, "defaultUnitCode");
        defaultUnitCode = defaultUnitCode == null ? null : defaultUnitCode.toLowerCase(Locale.ROOT);
        if (preparationState == null) {
            throw new IllegalArgumentException("preparationState is required");
        }
        if (yieldFactor == null
                || yieldFactor.signum() <= 0
                || yieldFactor.compareTo(new BigDecimal("10")) > 0
                || yieldFactor.scale() > 4
                || yieldFactor.precision() > 6) {
            throw new IllegalArgumentException("Invalid yieldFactor");
        }
        if (aliases == null) {
            throw new IllegalArgumentException("aliases is required");
        }
        aliases = List.copyOf(aliases);
    }

    private static String requireText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.trim();
    }

    private static String optionalText(String value, int maximumLength, String field) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }
}
