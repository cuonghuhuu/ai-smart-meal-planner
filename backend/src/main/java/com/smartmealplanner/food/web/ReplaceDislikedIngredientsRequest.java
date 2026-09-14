package com.smartmealplanner.food.web;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.food.DislikedIngredientStrength;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ReplaceDislikedIngredientsRequest(
        @NotNull List<@Valid Item> ingredients) {
    public record Item(
            @NotNull UUID ingredientPublicId,
            @NotNull DislikedIngredientStrength strength,
            String note) { }
}
