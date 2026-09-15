package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.smartmealplanner.nutrition.persistence.Nutrient;

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

/**
 * Module-private normalized nutrition fact. Nutrient and unit vocabulary remain
 * owned by Nutrition; this mapping only holds the V001 foreign-key reference.
 */
@Entity
@Table(name = "food_nutrients")
class FoodNutrient {

    @EmbeddedId
    private FoodNutrientId id = new FoodNutrientId();

    @MapsId("foodId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @MapsId("nutrientId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nutrient_id", nullable = false)
    private Nutrient nutrient;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_quality", nullable = false, length = 20)
    private FoodNutrientDataQuality dataQuality;

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

    protected FoodNutrient() {
    }

    FoodNutrient(
            Food food,
            Nutrient nutrient,
            BigDecimal amount,
            FoodNutrientDataQuality dataQuality) {

        if (food == null) {
            throw new IllegalArgumentException("food is required");
        }

        if (nutrient == null) {
            throw new IllegalArgumentException("nutrient is required");
        }

        if (nutrient.id() == null) {
            throw new IllegalArgumentException("nutrient.id is required");
        }

        this.food = food;
        this.nutrient = nutrient;
        correct(amount, dataQuality);
    }

    FoodNutrientId id() {
        return id;
    }

    Food food() {
        return food;
    }

    Nutrient nutrient() {
        return nutrient;
    }

    BigDecimal amount() {
        return amount;
    }

    FoodNutrientDataQuality dataQuality() {
        return dataQuality;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    LocalDateTime updatedAt() {
        return updatedAt;
    }

    void correct(
            BigDecimal amount,
            FoodNutrientDataQuality dataQuality) {

        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }

        if (dataQuality == null) {
            throw new IllegalArgumentException("dataQuality is required");
        }

        this.amount = amount;
        this.dataQuality = dataQuality;
    }
}
