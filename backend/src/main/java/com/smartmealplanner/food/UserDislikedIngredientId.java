package com.smartmealplanner.food;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class UserDislikedIngredientId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ingredient_id")
    private Long ingredientId;

    protected UserDislikedIngredientId() {
    }

    UserDislikedIngredientId(Long userId, Long ingredientId) {
        this.userId = userId;
        this.ingredientId = ingredientId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserDislikedIngredientId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(ingredientId, that.ingredientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, ingredientId);
    }
}
