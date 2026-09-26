package com.smartmealplanner.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_meal_slot_types")
@IdClass(RecipeMealSlotTypeId.class)
class RecipeMealSlotType {

    @Id
    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Id
    @Column(name = "meal_slot_type_id", nullable = false)
    private Long mealSlotTypeId;

    protected RecipeMealSlotType() {
    }

    RecipeMealSlotType(Long recipeId, Long mealSlotTypeId) {
        if (recipeId == null || mealSlotTypeId == null) {
            throw new IllegalArgumentException(
                    "recipeId and mealSlotTypeId are required");
        }
        this.recipeId = recipeId;
        this.mealSlotTypeId = mealSlotTypeId;
    }

    Long recipeId() { return recipeId; }
    Long mealSlotTypeId() { return mealSlotTypeId; }
}
