package com.smartmealplanner.food;

import java.util.UUID;

public record DislikedIngredientDraft(
        UUID ingredientPublicId,
        DislikedIngredientStrength strength,
        String note) {
}
