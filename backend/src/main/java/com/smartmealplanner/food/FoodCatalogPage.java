package com.smartmealplanner.food;

import java.util.List;

public record FoodCatalogPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<FoodSummaryView> content) {
}
