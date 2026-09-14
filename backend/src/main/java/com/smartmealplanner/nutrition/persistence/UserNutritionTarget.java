package com.smartmealplanner.nutrition.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_nutrition_targets")
public class UserNutritionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NutritionTargetOrigin origin;

    // These are scalar foreign-key snapshots by design. The referenced
    // activity-level and nutrition-goal persistence belongs to the profile
    // module and is not imported into Nutrition.
    @Column(name = "activity_level_id")
    private Long activityLevelId;

    @Column(name = "nutrition_goal_id")
    private Long nutritionGoalId;

    @Column(name = "calculation_method", length = 40)
    private String calculationMethod;

    @Column(length = 255)
    private String note;

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

    protected UserNutritionTarget() {
    }

    public UserNutritionTarget(
            Long userId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            NutritionTargetOrigin origin,
            Long activityLevelId,
            Long nutritionGoalId,
            String calculationMethod,
            String note) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required");
        }

        if (effectiveFrom == null) {
            throw new IllegalArgumentException(
                    "effectiveFrom is required");
        }

        if (effectiveTo != null
                && effectiveTo.isBefore(effectiveFrom)) {

            throw new IllegalArgumentException(
                    "effectiveTo must not be before effectiveFrom");
        }

        this.userId = userId;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        if (origin == null) {
            throw new IllegalArgumentException(
                    "origin is required");
        }

        this.origin = origin;
        this.activityLevelId = activityLevelId;
        this.nutritionGoalId = nutritionGoalId;
        this.calculationMethod = calculationMethod;
        this.note = note;
    }

    public Long id() {
        return id;
    }

    public Long userId() {
        return userId;
    }

    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate effectiveTo() {
        return effectiveTo;
    }

    public NutritionTargetOrigin origin() {
        return origin;
    }

    public Long activityLevelId() {
        return activityLevelId;
    }

    public Long nutritionGoalId() {
        return nutritionGoalId;
    }

    public String calculationMethod() {
        return calculationMethod;
    }

    public String note() {
        return note;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public void setEffectiveTo(
            LocalDate effectiveTo) {

        if (effectiveTo != null
                && effectiveTo.isBefore(effectiveFrom)) {

            throw new IllegalArgumentException(
                    "effectiveTo must not be before effectiveFrom");
        }

        this.effectiveTo = effectiveTo;
    }
}
