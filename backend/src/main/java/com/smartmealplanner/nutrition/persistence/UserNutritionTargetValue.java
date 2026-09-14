package com.smartmealplanner.nutrition.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_nutrition_target_values")
public class UserNutritionTargetValue {

    @EmbeddedId
    private UserNutritionTargetValueId id =
            new UserNutritionTargetValueId();

    @MapsId("targetId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private UserNutritionTarget target;

    @MapsId("nutrientId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nutrient_id", nullable = false)
    private Nutrient nutrient;

    @Column(name = "target_amount", precision = 12, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "min_amount", precision = 12, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 12, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "is_hard_limit", nullable = false)
    private boolean hardLimit;

    protected UserNutritionTargetValue() {
    }

    public UserNutritionTargetValue(
            UserNutritionTarget target,
            Nutrient nutrient,
            BigDecimal targetAmount,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            boolean hardLimit) {

        if (target == null) {
            throw new IllegalArgumentException(
                    "target is required");
        }

        if (nutrient == null) {
            throw new IllegalArgumentException(
                    "nutrient is required");
        }

        if (target.id() == null) {
            throw new IllegalArgumentException(
                    "target.id is required");
        }

        if (nutrient.id() == null) {
            throw new IllegalArgumentException(
                    "nutrient.id is required");
        }

        validateAmounts(
                targetAmount,
                minAmount,
                maxAmount);

        this.id = new UserNutritionTargetValueId(
                target.id(),
                nutrient.id());
        this.target = target;
        this.nutrient = nutrient;
        this.targetAmount = targetAmount;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.hardLimit = hardLimit;
    }

    public UserNutritionTargetValueId id() {
        return id;
    }

    public UserNutritionTarget target() {
        return target;
    }

    public Nutrient nutrient() {
        return nutrient;
    }

    public BigDecimal targetAmount() {
        return targetAmount;
    }

    public BigDecimal minAmount() {
        return minAmount;
    }

    public BigDecimal maxAmount() {
        return maxAmount;
    }

    public boolean isHardLimit() {
        return hardLimit;
    }

    private static void validateAmounts(
            BigDecimal targetAmount,
            BigDecimal minAmount,
            BigDecimal maxAmount) {

        if (targetAmount == null
                && minAmount == null
                && maxAmount == null) {

            throw new IllegalArgumentException(
                    "at least one target amount or bound is required");
        }

        if (isNegative(targetAmount)
                || isNegative(minAmount)
                || isNegative(maxAmount)) {

            throw new IllegalArgumentException(
                    "target amounts and bounds must not be negative");
        }

        if (minAmount != null
                && maxAmount != null
                && maxAmount.compareTo(minAmount) < 0) {

            throw new IllegalArgumentException(
                    "maxAmount must not be less than minAmount");
        }

        if (targetAmount != null
                && ((minAmount != null
                        && targetAmount.compareTo(minAmount) < 0)
                    || (maxAmount != null
                        && targetAmount.compareTo(maxAmount) > 0))) {

            throw new IllegalArgumentException(
                    "targetAmount must be within its bounds");
        }
    }

    private static boolean isNegative(
            BigDecimal value) {

        return value != null && value.signum() < 0;
    }
}
