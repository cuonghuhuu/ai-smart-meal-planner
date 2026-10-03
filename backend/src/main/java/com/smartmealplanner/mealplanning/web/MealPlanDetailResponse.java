package com.smartmealplanner.mealplanning.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService.Detail;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

/** Public representation of a persisted meal-plan generation outcome. */
public record MealPlanDetailResponse(UUID mealPlanPublicId, UUID requestPublicId,
        GenerationStatus status, LocalDate startDate, LocalDate endDate,
        BigDecimal defaultServings, List<Entry> entries,
        List<UnfilledSlot> unfilledSlots) {

    static MealPlanDetailResponse from(Detail detail) {
        return new MealPlanDetailResponse(detail.mealPlanPublicId(),
                detail.requestPublicId(), detail.status(), detail.startDate(),
                detail.endDate(), detail.defaultServings(),
                detail.entries().stream()
                        .map(entry -> new Entry(entry.planDate(), entry.mealSlotCode(),
                                entry.recipePublicId(), entry.recipeTitle(),
                                entry.servings()))
                        .toList(),
                detail.unfilledSlots().stream()
                        .map(slot -> new UnfilledSlot(slot.planDate(),
                                slot.mealSlotCode(), slot.reasonCode(),
                                slot.explanation()))
                        .toList());
    }

    public record Entry(LocalDate planDate, MealSlotCode mealSlotCode,
            UUID recipePublicId, String recipeTitle, BigDecimal servings) {
    }

    public record UnfilledSlot(LocalDate planDate, MealSlotCode mealSlotCode,
            UnfilledSlotReasonCode reasonCode, String explanation) {
    }
}
