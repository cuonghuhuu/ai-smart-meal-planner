package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Normalized Food boundary. The display name is Vietnamese when the source
 * supplies one; sourceName is retained for traceability but is not guessed
 * into a separate database column that V001 does not provide.
 */
public record CatalogImportFood(
        String sourceIdentifier,
        String catalogCode,
        String displayName,
        String sourceName,
        String categoryCode,
        String description,
        NutritionBasis nutritionBasis,
        BigDecimal densityGPerMl,
        FoodSource source,
        String sourceReference,
        List<CatalogImportNutrientFact> nutrientFacts,
        CatalogImportIngredientMapping ingredientMapping) {

    public CatalogImportFood {
        sourceIdentifier = requireText(sourceIdentifier, 120, "sourceIdentifier");
        catalogCode = requireText(catalogCode, 80, "catalogCode");
        displayName = requireText(displayName, 200, "displayName");
        sourceName = optionalText(sourceName, 200, "sourceName");
        categoryCode = requireText(categoryCode, 60, "categoryCode")
                .toUpperCase(Locale.ROOT);
        description = optionalText(description, 500, "description");
        if (nutritionBasis == null) {
            throw new IllegalArgumentException("nutritionBasis is required");
        }
        if (densityGPerMl != null
                && (densityGPerMl.signum() <= 0
                || densityGPerMl.compareTo(new BigDecimal("25")) >= 0
                || densityGPerMl.scale() > 4
                || densityGPerMl.precision() > 8)) {
            throw new IllegalArgumentException("Invalid densityGPerMl");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        sourceReference = requireText(sourceReference, 255, "sourceReference");
        if (nutrientFacts == null) {
            throw new IllegalArgumentException("nutrientFacts is required");
        }
        nutrientFacts = List.copyOf(nutrientFacts);
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
        return value == null ? null : value.trim();
    }
}
