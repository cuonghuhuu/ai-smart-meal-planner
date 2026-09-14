package com.smartmealplanner.nutrition.web;

import java.util.List;

/** Bounded public page of Nutrition target snapshots. */
public record NutritionTargetHistoryResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<NutritionTargetResponse> content) {
}
