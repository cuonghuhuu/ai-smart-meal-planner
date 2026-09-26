package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RecipeNutritionSnapshotView(
        LocalDateTime computedAt,
        Integer ingredientRevision,
        BigDecimal completenessRatio,
        String computationNote,
        List<RecipeNutritionValueView> values) {
}
