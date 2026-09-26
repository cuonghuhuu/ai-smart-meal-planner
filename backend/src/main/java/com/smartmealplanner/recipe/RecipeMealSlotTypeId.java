package com.smartmealplanner.recipe;

import java.io.Serializable;
import java.util.Objects;

final class RecipeMealSlotTypeId implements Serializable {
    private Long recipeId;
    private Long mealSlotTypeId;

    protected RecipeMealSlotTypeId() {
    }

    RecipeMealSlotTypeId(Long recipeId, Long mealSlotTypeId) {
        this.recipeId = recipeId;
        this.mealSlotTypeId = mealSlotTypeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeMealSlotTypeId value)) return false;
        return Objects.equals(recipeId, value.recipeId)
                && Objects.equals(mealSlotTypeId, value.mealSlotTypeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId, mealSlotTypeId);
    }
}
