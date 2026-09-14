package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Module-private measured Food serving fact; it never guesses a conversion. */
@Entity
@Table(
        name = "food_servings",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_food_servings_food_name",
                columnNames = {"food_id", "display_name"}))
class FoodServing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private MeasurementUnit unit;

    @Column(name = "gram_weight", precision = 12, scale = 4)
    private BigDecimal gramWeight;

    @Column(precision = 12, scale = 4)
    private BigDecimal milliliters;

    @Column(name = "is_default", nullable = false)
    private boolean defaultServing;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected FoodServing() {
    }

    FoodServing(
            Food food,
            String displayName,
            BigDecimal quantity,
            MeasurementUnit unit,
            BigDecimal gramWeight,
            BigDecimal milliliters,
            boolean defaultServing) {

        if (food == null) {
            throw new IllegalArgumentException("food is required");
        }

        if (displayName == null
                || displayName.isBlank()
                || displayName.length() > 80) {

            throw new IllegalArgumentException("Invalid displayName");
        }

        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        if (unit == null) {
            throw new IllegalArgumentException("unit is required");
        }

        if (unit.id() == null) {
            throw new IllegalArgumentException("unit.id is required");
        }

        if (gramWeight != null && gramWeight.signum() <= 0) {
            throw new IllegalArgumentException("gramWeight must be positive");
        }

        if (milliliters != null && milliliters.signum() <= 0) {
            throw new IllegalArgumentException("milliliters must be positive");
        }

        if (gramWeight == null && milliliters == null) {
            throw new IllegalArgumentException(
                    "gramWeight or milliliters is required");
        }

        this.food = food;
        this.displayName = displayName;
        this.quantity = quantity;
        this.unit = unit;
        this.gramWeight = gramWeight;
        this.milliliters = milliliters;
        this.defaultServing = defaultServing;
    }

    Long id() {
        return id;
    }

    Food food() {
        return food;
    }

    String displayName() {
        return displayName;
    }

    BigDecimal quantity() {
        return quantity;
    }

    MeasurementUnit unit() {
        return unit;
    }

    BigDecimal gramWeight() {
        return gramWeight;
    }

    BigDecimal milliliters() {
        return milliliters;
    }

    boolean isDefaultServing() {
        return defaultServing;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    LocalDateTime updatedAt() {
        return updatedAt;
    }
}
