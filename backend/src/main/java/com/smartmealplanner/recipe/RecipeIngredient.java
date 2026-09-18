package com.smartmealplanner.recipe;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Recipe line; cross-module catalog references remain scalar FK handles. */
@Entity
@Table(name = "recipe_ingredients")
class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "line_number", nullable = false)
    private Short lineNumber;

    @Column(name = "ingredient_id", nullable = false)
    private Long ingredientId;

    @Column(name = "food_id")
    private Long foodId;

    @Column(precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "preparation_note", length = 200)
    private String preparationNote;

    @Column(name = "is_optional", nullable = false)
    private boolean optional;

    @Column(name = "allow_substitution", nullable = false)
    private boolean allowSubstitution;

    @Column(name = "section_label", length = 80)
    private String sectionLabel;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected RecipeIngredient() {
    }

    RecipeIngredient(
            Long recipeId,
            Integer lineNumber,
            Long ingredientId,
            Long foodId,
            BigDecimal quantity,
            Long unitId,
            String preparationNote,
            boolean optional,
            boolean allowSubstitution,
            String sectionLabel) {

        if (recipeId == null) {
            throw new IllegalArgumentException("recipeId is required");
        }
        if (lineNumber == null || lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be at least 1");
        }
        if (ingredientId == null) {
            throw new IllegalArgumentException("ingredientId is required");
        }
        validateQuantityUnit(quantity, unitId);
        this.recipeId = recipeId;
        this.lineNumber = RecipeSmallInt.toShort(lineNumber, "lineNumber");
        this.ingredientId = ingredientId;
        this.foodId = foodId;
        this.quantity = quantity;
        this.unitId = unitId;
        this.preparationNote = bounded(preparationNote, 200, "preparationNote");
        this.optional = optional;
        this.allowSubstitution = allowSubstitution;
        this.sectionLabel = bounded(sectionLabel, 80, "sectionLabel");
    }

    Long internalId() { return id; }
    Long recipeId() { return recipeId; }
    Short lineNumber() { return lineNumber; }
    Long ingredientId() { return ingredientId; }
    Long foodId() { return foodId; }
    BigDecimal quantity() { return quantity; }
    Long unitId() { return unitId; }
    String preparationNote() { return preparationNote; }
    boolean isOptional() { return optional; }
    boolean allowSubstitution() { return allowSubstitution; }
    String sectionLabel() { return sectionLabel; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }

    static void validateQuantityUnit(BigDecimal quantity, Long unitId) {
        if ((quantity == null) != (unitId == null)) {
            throw new IllegalArgumentException(
                    "quantity and unitId must be specified together");
        }
        if (quantity != null && quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    private static String bounded(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }
}
