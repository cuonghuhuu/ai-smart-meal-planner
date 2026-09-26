package com.smartmealplanner.recipe;

import java.time.LocalTime;

public record RecipeMealSlotView(
        String code,
        String displayName,
        Short displayOrder,
        LocalTime typicalTime,
        boolean mainMeal) {
}
