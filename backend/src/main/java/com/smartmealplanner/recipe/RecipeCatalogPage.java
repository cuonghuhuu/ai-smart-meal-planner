package com.smartmealplanner.recipe;

import java.util.List;

public record RecipeCatalogPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<RecipeSummaryView> content) {
}
