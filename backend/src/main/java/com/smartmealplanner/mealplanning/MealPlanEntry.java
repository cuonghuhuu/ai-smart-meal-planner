package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
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
@Table(name = "meal_plan_entries")
class MealPlanEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "meal_plan_id", nullable = false)
    private Long mealPlanId;
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;
    @Column(name = "meal_slot_type_id", nullable = false)
    private Long mealSlotTypeId;
    @Column(name = "position_in_slot", nullable = false)
    private Short positionInSlot;
    @Column(name = "recipe_id")
    private Long recipeId;
    @Column(name = "food_id")
    private Long foodId;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal servings;
    @Column(name = "food_serving_id")
    private Long foodServingId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealPlanEntryProvenance provenance;
    @Column(name = "source_result_id")
    private Long sourceResultId;
    @Enumerated(EnumType.STRING)
    @Column(name = "consumption_status", nullable = false, length = 20)
    private MealPlanConsumptionStatus consumptionStatus;
    @Column(name = "consumed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime consumedAt;
    @Column(length = 255)
    private String note;
    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected MealPlanEntry() {
    }

    MealPlanEntry(Long mealPlanId, LocalDate planDate, Long mealSlotTypeId,
            int positionInSlot, Long recipeId, BigDecimal servings,
            Long sourceResultId) {
        if (mealPlanId == null || planDate == null || mealSlotTypeId == null
                || positionInSlot < 1 || positionInSlot > Short.MAX_VALUE
                || recipeId == null || servings == null || servings.signum() <= 0
                || servings.compareTo(BigDecimal.valueOf(50)) > 0 || sourceResultId == null) {
            throw new IllegalArgumentException("Invalid meal plan entry");
        }
        this.mealPlanId = mealPlanId;
        this.planDate = planDate;
        this.mealSlotTypeId = mealSlotTypeId;
        this.positionInSlot = (short) positionInSlot;
        this.recipeId = recipeId;
        this.servings = servings;
        this.provenance = MealPlanEntryProvenance.AI_GENERATED;
        this.sourceResultId = sourceResultId;
        this.consumptionStatus = MealPlanConsumptionStatus.PLANNED;
    }

    Long internalId() { return id; }
    Long mealPlanId() { return mealPlanId; }
    LocalDate planDate() { return planDate; }
    Long mealSlotTypeId() { return mealSlotTypeId; }
    Short positionInSlot() { return positionInSlot; }
    Long recipeId() { return recipeId; }
    BigDecimal servings() { return servings; }
    MealPlanEntryProvenance provenance() { return provenance; }
    Long sourceResultId() { return sourceResultId; }
    MealPlanConsumptionStatus consumptionStatus() { return consumptionStatus; }
}
