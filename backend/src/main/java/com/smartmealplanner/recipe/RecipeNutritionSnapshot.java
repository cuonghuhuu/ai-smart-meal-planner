package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_nutrition_snapshots")
class RecipeNutritionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "computed_at", nullable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime computedAt;

    @Column(name = "ingredient_revision", nullable = false)
    private Integer ingredientRevision;

    @Column(name = "completeness_ratio", nullable = false,
            precision = 5, scale = 4)
    private BigDecimal completenessRatio;

    @Column(name = "computation_note", length = 255)
    private String computationNote;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    protected RecipeNutritionSnapshot() {
    }

    RecipeNutritionSnapshot(Long recipeId, LocalDateTime computedAt,
            Integer ingredientRevision, BigDecimal completenessRatio,
            String computationNote, boolean current) {
        if (recipeId == null || computedAt == null || ingredientRevision == null
                || ingredientRevision < 0) {
            throw new IllegalArgumentException("Invalid snapshot identity");
        }
        if (completenessRatio == null || completenessRatio.signum() < 0
                || completenessRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Invalid completenessRatio");
        }
        if (computationNote != null && computationNote.length() > 255) {
            throw new IllegalArgumentException("Invalid computationNote");
        }
        this.recipeId = recipeId;
        this.computedAt = computedAt;
        this.ingredientRevision = ingredientRevision;
        this.completenessRatio = completenessRatio;
        this.computationNote = computationNote;
        this.current = current;
    }

    Long internalId() { return id; }
    Long recipeId() { return recipeId; }
    LocalDateTime computedAt() { return computedAt; }
    Integer ingredientRevision() { return ingredientRevision; }
    BigDecimal completenessRatio() { return completenessRatio; }
    String computationNote() { return computationNote; }
    boolean isCurrent() { return current; }
}
