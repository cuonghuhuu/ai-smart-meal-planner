package com.smartmealplanner.recipe;

import java.io.Serializable;
import java.util.Objects;

final class RecipeStepId implements Serializable {
    private Long recipeId;
    private Short stepNumber;

    protected RecipeStepId() {
    }

    RecipeStepId(Long recipeId, Integer stepNumber) {
        this.recipeId = recipeId;
        this.stepNumber = RecipeSmallInt.toShort(stepNumber, "stepNumber");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeStepId value)) return false;
        return Objects.equals(recipeId, value.recipeId)
                && Objects.equals(stepNumber, value.stepNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipeId, stepNumber);
    }
}
