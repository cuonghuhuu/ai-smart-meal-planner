package com.smartmealplanner.recipe;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_nutrition_values")
@IdClass(RecipeNutritionValueId.class)
class RecipeNutritionValue {

    @Id
    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Id
    @Column(name = "nutrient_id", nullable = false)
    private Long nutrientId;

    @Column(name = "amount_per_serving", nullable = false,
            precision = 12, scale = 4)
    private BigDecimal amountPerServing;

    protected RecipeNutritionValue() {
    }

    RecipeNutritionValue(Long snapshotId, Long nutrientId,
            BigDecimal amountPerServing) {
        if (snapshotId == null || nutrientId == null
                || amountPerServing == null
                || amountPerServing.signum() < 0) {
            throw new IllegalArgumentException("Invalid nutrition value");
        }
        this.snapshotId = snapshotId;
        this.nutrientId = nutrientId;
        this.amountPerServing = amountPerServing;
    }

    Long snapshotId() { return snapshotId; }
    Long nutrientId() { return nutrientId; }
    BigDecimal amountPerServing() { return amountPerServing; }
}
