package com.smartmealplanner.nutrition.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UserNutritionTargetValueId
        implements Serializable {

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "nutrient_id", nullable = false)
    private Long nutrientId;

    protected UserNutritionTargetValueId() {
    }

    public UserNutritionTargetValueId(
            Long targetId,
            Long nutrientId) {

        this.targetId = targetId;
        this.nutrientId = nutrientId;
    }

    public Long targetId() {
        return targetId;
    }

    public Long nutrientId() {
        return nutrientId;
    }

    @Override
    public boolean equals(
            Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof UserNutritionTargetValueId that)) {
            return false;
        }

        return Objects.equals(targetId, that.targetId)
                && Objects.equals(nutrientId, that.nutrientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetId, nutrientId);
    }
}
