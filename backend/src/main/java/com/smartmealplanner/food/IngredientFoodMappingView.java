package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.UUID;

public record IngredientFoodMappingView(
        UUID foodPublicId,
        String foodCode,
        String foodDisplayName,
        IngredientPreparationState preparationState,
        BigDecimal yieldFactor,
        boolean primary) {
}
