package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.smartmealplanner.recommendation.persistence.RecommendationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_plan_entries")
public class MealPlanEntry {
    public enum Provenance { MANUAL, AI_GENERATED, AI_EDITED, COPIED_FROM_PLAN }
    public enum ConsumptionStatus { PLANNED, EATEN, SKIPPED, REPLACED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan plan;
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
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Provenance provenance;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_result_id")
    private RecommendationResult sourceResult;
    @Enumerated(EnumType.STRING) @Column(name = "consumption_status", nullable = false, length = 20)
    private ConsumptionStatus consumptionStatus;
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

    protected MealPlanEntry() { }

    public MealPlanEntry(MealPlan plan, LocalDate planDate, Long mealSlotTypeId,
            short positionInSlot, Long recipeId, Long foodId, BigDecimal servings,
            Provenance provenance, RecommendationResult sourceResult) {
        if (plan == null || planDate == null || planDate.isBefore(plan.startDate())
                || planDate.isAfter(plan.endDate()) || mealSlotTypeId == null
                || positionInSlot < 1 || (recipeId == null) == (foodId == null)
                || servings == null || servings.signum() <= 0
                || servings.compareTo(new BigDecimal("50")) > 0 || provenance == null) {
            throw new IllegalArgumentException("Invalid meal plan entry");
        }
        this.plan = plan;
        this.planDate = planDate;
        this.mealSlotTypeId = mealSlotTypeId;
        this.positionInSlot = positionInSlot;
        this.recipeId = recipeId;
        this.foodId = foodId;
        this.servings = servings;
        this.provenance = provenance;
        this.sourceResult = sourceResult;
        this.consumptionStatus = ConsumptionStatus.PLANNED;
    }

    public Long id() { return id; }
    public MealPlan plan() { return plan; }
    public LocalDate planDate() { return planDate; }
    public Long mealSlotTypeId() { return mealSlotTypeId; }
    public Short positionInSlot() { return positionInSlot; }
    public Long recipeId() { return recipeId; }
    public Long foodId() { return foodId; }
    public BigDecimal servings() { return servings; }
    public Long foodServingId() { return foodServingId; }
    public Provenance provenance() { return provenance; }
    public RecommendationResult sourceResult() { return sourceResult; }
    public ConsumptionStatus consumptionStatus() { return consumptionStatus; }
    public LocalDateTime consumedAt() { return consumedAt; }
    public String note() { return note; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
