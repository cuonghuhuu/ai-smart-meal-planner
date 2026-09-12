package com.smartmealplanner.profile.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sex sex;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_level_id")
    private ActivityLevel activityLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nutrition_goal_id")
    private NutritionGoal nutritionGoal;

    @Column(name = "target_weight_kg", precision = 5, scale = 2)
    private BigDecimal targetWeightKg;

    @Column(name = "weekly_change_kg", precision = 4, scale = 2)
    private BigDecimal weeklyChangeKg;

    @Column(name = "household_size", nullable = false)
    private Byte householdSize;

    @Column(name = "max_cook_minutes")
    private Short maxCookMinutes;

    @Column(length = 500)
    private String notes;

    @Version
    @Column(nullable = false)
    private Long version;

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

    protected UserProfile() {
    }

    public UserProfile(
            Long userId) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "userId is required");
        }

        this.userId = userId;
        this.householdSize = (byte) 1;
    }

    public Long userId() {
        return userId;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public Sex sex() {
        return sex;
    }

    public BigDecimal heightCm() {
        return heightCm;
    }

    public ActivityLevel activityLevel() {
        return activityLevel;
    }

    public NutritionGoal nutritionGoal() {
        return nutritionGoal;
    }

    public BigDecimal targetWeightKg() {
        return targetWeightKg;
    }

    public BigDecimal weeklyChangeKg() {
        return weeklyChangeKg;
    }

    public Byte householdSize() {
        return householdSize;
    }

    public Short maxCookMinutes() {
        return maxCookMinutes;
    }

    public String notes() {
        return notes;
    }

    public Long version() {
        return version;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public void update(
            LocalDate birthDate,
            Sex sex,
            BigDecimal heightCm,
            ActivityLevel activityLevel,
            NutritionGoal nutritionGoal,
            BigDecimal targetWeightKg,
            BigDecimal weeklyChangeKg,
            Byte householdSize,
            Short maxCookMinutes,
            String notes) {

        this.birthDate = birthDate;
        this.sex = sex;
        this.heightCm = heightCm;
        this.activityLevel = activityLevel;
        this.nutritionGoal = nutritionGoal;
        this.targetWeightKg = targetWeightKg;
        this.weeklyChangeKg = weeklyChangeKg;
        this.householdSize = householdSize == null ? (byte) 1 : householdSize;
        this.maxCookMinutes = maxCookMinutes;
        this.notes = notes;
    }
}
