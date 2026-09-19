package com.smartmealplanner.food;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class IngredientAllergenId implements Serializable {

    @Column(name = "ingredient_id")
    private Long ingredientId;

    @Column(name = "allergen_id")
    private Long allergenId;

    protected IngredientAllergenId() {
    }

    IngredientAllergenId(Long ingredientId, Long allergenId) {
        this.ingredientId = ingredientId;
        this.allergenId = allergenId;
    }

    Long ingredientId() { return ingredientId; }
    Long allergenId() { return allergenId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IngredientAllergenId that)) {
            return false;
        }
        return Objects.equals(ingredientId, that.ingredientId)
                && Objects.equals(allergenId, that.allergenId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingredientId, allergenId);
    }
}
