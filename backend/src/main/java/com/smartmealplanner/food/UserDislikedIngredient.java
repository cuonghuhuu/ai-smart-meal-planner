package com.smartmealplanner.food;

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

/** User-private preference row; it deliberately stores the owner ID as a scalar. */
@Entity
@Table(name = "user_disliked_ingredients")
class UserDislikedIngredient {
    @EmbeddedId private UserDislikedIngredientId id = new UserDislikedIngredientId();
    @Column(name = "user_id", insertable = false, updatable = false) private Long userId;
    @MapsId("ingredientId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false) private Ingredient ingredient;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DislikedIngredientStrength strength;
    @Column(length = 255) private String note;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)") private LocalDateTime createdAt;
    protected UserDislikedIngredient() { }
    UserDislikedIngredient(Long userId, Ingredient ingredient, DislikedIngredientStrength strength, String note) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (ingredient == null || ingredient.internalId() == null) throw new IllegalArgumentException("ingredient is required");
        if (strength == null) throw new IllegalArgumentException("strength is required");
        if (note != null && note.length() > 255) throw new IllegalArgumentException("Invalid note");
        this.id = new UserDislikedIngredientId(userId, ingredient.internalId());
        this.userId = userId; this.ingredient = ingredient; this.strength = strength; this.note = note;
    }
    Ingredient ingredient() { return ingredient; }
    DislikedIngredientStrength strength() { return strength; }
    String note() { return note; }
}
