package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingredient_foods")
class IngredientFood {
    @EmbeddedId private IngredientFoodId id = new IngredientFoodId();
    @MapsId("ingredientId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false) private Ingredient ingredient;
    @MapsId("foodId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false) private Food food;
    @Enumerated(EnumType.STRING) @Column(name = "preparation_state", nullable = false, length = 20)
    private IngredientPreparationState preparationState;
    @Column(name = "yield_factor", nullable = false, precision = 6, scale = 4) private BigDecimal yieldFactor;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
    protected IngredientFood() { }
    IngredientFood(Ingredient ingredient, Food food, IngredientPreparationState preparationState,
            BigDecimal yieldFactor, boolean primary) {
        if (ingredient == null) throw new IllegalArgumentException("ingredient is required");
        if (food == null || food.internalId() == null) throw new IllegalArgumentException("food is required");
        if (preparationState == null) throw new IllegalArgumentException("preparationState is required");
        if (yieldFactor == null || yieldFactor.signum() <= 0 || yieldFactor.compareTo(new BigDecimal("10")) > 0)
            throw new IllegalArgumentException("yieldFactor must be greater than zero and at most 10");
        this.ingredient = ingredient; this.food = food; this.preparationState = preparationState;
        this.yieldFactor = yieldFactor; this.primary = primary;
    }
    Food food() { return food; }
    IngredientPreparationState preparationState() { return preparationState; }
    BigDecimal yieldFactor() { return yieldFactor; }
    boolean isPrimary() { return primary; }
    LocalDateTime createdAt() { return createdAt; }
}
