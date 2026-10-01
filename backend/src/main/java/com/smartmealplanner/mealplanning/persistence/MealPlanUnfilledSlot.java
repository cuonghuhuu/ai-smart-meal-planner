package com.smartmealplanner.mealplanning.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
@Table(name = "meal_plan_unfilled_slots")
public class MealPlanUnfilledSlot {
    public enum Reason {
        NO_ELIGIBLE_RECIPE, HARD_CONSTRAINT_CONFLICT, UNSUPPORTED_HARD_CONSTRAINT,
        PANTRY_INFEASIBLE, NUTRITION_INFEASIBLE, SEARCH_LIMIT_REACHED
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan plan;
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;
    @Column(name = "meal_slot_type_id", nullable = false)
    private Long mealSlotTypeId;
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false, length = 60)
    private Reason reasonCode;
    @Column(length = 500)
    private String explanation;
    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected MealPlanUnfilledSlot() { }

    public MealPlanUnfilledSlot(MealPlan plan, LocalDate planDate, Long mealSlotTypeId,
            Reason reasonCode, String explanation) {
        if (plan == null || planDate == null || planDate.isBefore(plan.startDate())
                || planDate.isAfter(plan.endDate()) || mealSlotTypeId == null
                || reasonCode == null || explanation != null && explanation.length() > 500) {
            throw new IllegalArgumentException("Invalid unfilled slot");
        }
        this.plan = plan;
        this.planDate = planDate;
        this.mealSlotTypeId = mealSlotTypeId;
        this.reasonCode = reasonCode;
        this.explanation = explanation;
    }

    public Long id() { return id; }
    public MealPlan plan() { return plan; }
    public LocalDate planDate() { return planDate; }
    public Long mealSlotTypeId() { return mealSlotTypeId; }
    public Reason reasonCode() { return reasonCode; }
    public String explanation() { return explanation; }
    public LocalDateTime createdAt() { return createdAt; }
}
