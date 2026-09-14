package com.smartmealplanner.nutrition.application;

import java.util.List;

/**
 * Bounded application page for a user's nutrition-target history.
 */
public record NutritionTargetHistoryPage(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<NutritionTargetView> content) {

    public NutritionTargetHistoryPage {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative");
        }

        if (size < 1) {
            throw new IllegalArgumentException(
                    "size must be positive");
        }

        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException(
                    "page metadata must not be negative");
        }

        if (content == null) {
            throw new IllegalArgumentException(
                    "content is required");
        }

        content = List.copyOf(content);
    }
}
