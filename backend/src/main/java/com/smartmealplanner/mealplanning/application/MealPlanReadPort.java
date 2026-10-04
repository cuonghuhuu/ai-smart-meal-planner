package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

/** Owner-scoped read contract for a persisted meal-plan generation outcome. */
public interface MealPlanReadPort {
    Optional<Header> findOwnedPlan(UUID mealPlanPublicId, Long ownerId);

    List<Entry> findEntries(Long mealPlanId);

    List<UnfilledSlot> findUnfilledSlots(Long mealPlanId);

    record Header(Long internalId, UUID mealPlanPublicId, UUID requestPublicId,
            GenerationStatus status, LocalDate startDate, LocalDate endDate,
            BigDecimal defaultServings) {
    }

    record Entry(LocalDate planDate, MealSlotCode mealSlotCode,
            UUID recipePublicId, String recipeTitle, BigDecimal servings) {
    }

    record UnfilledSlot(LocalDate planDate, MealSlotCode mealSlotCode,
            UnfilledSlotReasonCode reasonCode, String explanation) {
    }
}
