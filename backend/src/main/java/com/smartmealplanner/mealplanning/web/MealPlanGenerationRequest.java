package com.smartmealplanner.mealplanning.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.smartmealplanner.mealplanning.application.MealPlanGenerationCommand;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;

import jakarta.validation.constraints.NotNull;

/** Public generation constraints supplied by the authenticated user. */
public record MealPlanGenerationRequest(
        @NotNull LocalDate startDate,
        @NotNull Integer days,
        @NotNull List<MealSlotCode> requestedMealSlots,
        @NotNull BigDecimal defaultServings,
        Integer maxMinutesPerMeal) {
    MealPlanGenerationCommand toCommand() {
        return new MealPlanGenerationCommand(startDate, days,
                requestedMealSlots, defaultServings, maxMinutesPerMeal);
    }
}
