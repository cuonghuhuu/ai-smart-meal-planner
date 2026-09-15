package com.smartmealplanner.food;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class FoodNutrientId implements Serializable {

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "nutrient_id", nullable = false)
    private Long nutrientId;

    protected FoodNutrientId() {
    }

    FoodNutrientId(
            Long foodId,
            Long nutrientId) {

        this.foodId = foodId;
        this.nutrientId = nutrientId;
    }

    Long foodId() {
        return foodId;
    }

    Long nutrientId() {
        return nutrientId;
    }

    @Override
    public boolean equals(
            Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof FoodNutrientId that)) {
            return false;
        }

        return Objects.equals(foodId, that.foodId)
                && Objects.equals(nutrientId, that.nutrientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foodId, nutrientId);
    }
}
