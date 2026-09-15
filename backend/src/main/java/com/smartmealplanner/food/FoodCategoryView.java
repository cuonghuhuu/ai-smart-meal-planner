package com.smartmealplanner.food;

public record FoodCategoryView(
        String code,
        String displayName,
        String parentCategoryCode,
        String description) {
}
