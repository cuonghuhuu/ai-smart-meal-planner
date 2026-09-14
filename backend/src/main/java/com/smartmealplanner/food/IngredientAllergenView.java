package com.smartmealplanner.food;

public record IngredientAllergenView(
        String allergenCode,
        String allergenDisplayName,
        IngredientAllergenPresence presence,
        String note) {
}
