package com.smartmealplanner.food;

/** A visible validation or reporting outcome from an offline catalog import. */
public enum CatalogImportIssueType {
    INVALID_SOURCE,
    UNKNOWN_CATEGORY,
    UNKNOWN_UNIT,
    UNSUPPORTED_NUTRIENT,
    UNIT_MISMATCH,
    INVALID_NUTRIENT_QUALITY,
    DUPLICATE_NUTRIENT,
    DUPLICATE_FOOD,
    DUPLICATE_ALIAS,
    ALIAS_CONFLICT,
    FOOD_CODE_CONFLICT,
    INGREDIENT_MAPPING_CONFLICT
}
