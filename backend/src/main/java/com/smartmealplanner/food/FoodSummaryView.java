package com.smartmealplanner.food;

import java.util.UUID;

public record FoodSummaryView(
        UUID publicId,
        String code,
        String displayName,
        String brand,
        FoodCategoryView category) {
}
