package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * One normalized nutrient candidate. canonicalCode is null for an explicitly
 * unsupported source nutrient; such a candidate is reported and never stored.
 * Missing source values are represented by omitting the fact from the food.
 */
public record CatalogImportNutrientFact(
        String sourceCode,
        String canonicalCode,
        BigDecimal amount,
        String unitCode,
        FoodNutrientDataQuality dataQuality) {

    public CatalogImportNutrientFact {
        sourceCode = requireText(sourceCode, 120, "sourceCode");
        canonicalCode = optionalCode(canonicalCode);
        if (amount != null
                && (amount.signum() < 0
                || amount.scale() > 4
                || amount.precision() > 12)) {
            throw new IllegalArgumentException("Invalid nutrient amount");
        }
        if (amount != null) {
            unitCode = requireText(unitCode, 20, "unitCode").toLowerCase(Locale.ROOT);
        } else if (unitCode != null) {
            unitCode = unitCode.trim().toLowerCase(Locale.ROOT);
        }
    }

    private static String requireText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.trim();
    }

    private static String optionalCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 40) {
            throw new IllegalArgumentException("Invalid canonicalCode");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
