package com.smartmealplanner.food;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class IngredientFoodId implements Serializable {

    @Column(name = "ingredient_id")
    private Long ingredientId;

    @Column(name = "food_id")
    private Long foodId;

    protected IngredientFoodId() {
    }

    IngredientFoodId(Long ingredientId, Long foodId) {
        this.ingredientId = ingredientId;
        this.foodId = foodId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IngredientFoodId that)) {
            return false;
        }
        return Objects.equals(ingredientId, that.ingredientId)
                && Objects.equals(foodId, that.foodId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingredientId, foodId);
    }
}
