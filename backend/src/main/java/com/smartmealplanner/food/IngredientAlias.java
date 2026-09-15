package com.smartmealplanner.food;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingredient_aliases")
class IngredientAlias {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false) private Ingredient ingredient;
    @Column(nullable = false, unique = true, length = 150) private String alias;
    @Column(name = "alias_locale", length = 20) private String aliasLocale;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
    protected IngredientAlias() { }
    IngredientAlias(Ingredient ingredient, String alias, String aliasLocale) {
        if (ingredient == null) throw new IllegalArgumentException("ingredient is required");
        if (alias == null || alias.isBlank() || alias.length() > 150) throw new IllegalArgumentException("Invalid alias");
        if (aliasLocale != null && aliasLocale.length() > 20) throw new IllegalArgumentException("Invalid aliasLocale");
        this.ingredient = ingredient; this.alias = alias; this.aliasLocale = aliasLocale;
    }
    String alias() { return alias; }
    String aliasLocale() { return aliasLocale; }
    Ingredient ingredient() { return ingredient; }
    LocalDateTime createdAt() { return createdAt; }
}
