package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;

/** Gate-D application input; Gate E will supply it from an authenticated API. */
public record MealPlanGenerationCommand(LocalDate startDate, int days,
        List<MealSlotCode> requestedMealSlots, BigDecimal defaultServings,
        Integer maxMinutesPerMeal) {
    public MealPlanGenerationCommand {
        requestedMealSlots = requestedMealSlots == null
                ? null : Collections.unmodifiableList(new ArrayList<>(requestedMealSlots));
    }
}
