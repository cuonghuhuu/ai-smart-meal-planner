package com.smartmealplanner.food;

import java.util.List;

public record IngredientCatalogPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<IngredientSummaryView> content) {
}
