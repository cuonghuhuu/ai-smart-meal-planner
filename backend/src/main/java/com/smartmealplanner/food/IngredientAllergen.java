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

@Entity
@Table(name = "ingredient_allergens")
class IngredientAllergen {
    @EmbeddedId private IngredientAllergenId id = new IngredientAllergenId();
    @MapsId("ingredientId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false) private Ingredient ingredient;
    @Column(name = "allergen_id", insertable = false, updatable = false)
    private Long allergenId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IngredientAllergenPresence presence;
    @Column(length = 255) private String note;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)") private LocalDateTime createdAt;
    protected IngredientAllergen() { }
    IngredientAllergen(Ingredient ingredient, Long allergenId, IngredientAllergenPresence presence, String note) {
        if (ingredient == null) throw new IllegalArgumentException("ingredient is required");
        if (allergenId == null) throw new IllegalArgumentException("allergenId is required");
        if (presence == null) throw new IllegalArgumentException("presence is required");
        if (note != null && note.length() > 255) throw new IllegalArgumentException("Invalid note");
        this.id = new IngredientAllergenId(null, allergenId);
        this.ingredient = ingredient; this.allergenId = allergenId; this.presence = presence; this.note = note;
    }
    Long allergenId() { return allergenId == null ? id.allergenId() : allergenId; }
    IngredientAllergenPresence presence() { return presence; }
    String note() { return note; }
}
