package com.smartmealplanner.recipe;

import java.io.Serializable;
import java.util.Objects;

final class RecipeNutritionValueId implements Serializable {
    private Long snapshotId;
    private Long nutrientId;

    protected RecipeNutritionValueId() {
    }

    RecipeNutritionValueId(Long snapshotId, Long nutrientId) {
        this.snapshotId = snapshotId;
        this.nutrientId = nutrientId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeNutritionValueId value)) return false;
        return Objects.equals(snapshotId, value.snapshotId)
                && Objects.equals(nutrientId, value.nutrientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId, nutrientId);
    }
}
