package com.smartmealplanner.recipe;

import java.util.List;

public record RecipeAdminPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<RecipeAdminSummaryView> content) {
}
