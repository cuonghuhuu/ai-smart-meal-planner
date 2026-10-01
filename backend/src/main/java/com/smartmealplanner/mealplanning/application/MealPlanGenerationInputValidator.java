package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.util.HashSet;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits;

/** Shared validation of caller-supplied generation constraints. */
public final class MealPlanGenerationInputValidator {
    private MealPlanGenerationInputValidator() { }

    public static void validate(MealPlanGenerationCommand command) {
        if (command == null || command.startDate() == null || command.days() < 1
                || command.days() > MealPlanningContractLimits.MAX_PLAN_DAYS
                || command.requestedMealSlots() == null
                || command.requestedMealSlots().isEmpty()
                || command.requestedMealSlots().contains(null)
                || command.requestedMealSlots().size()
                        > MealPlanningContractLimits.MAX_REQUESTED_MEAL_SLOTS
                || new HashSet<>(command.requestedMealSlots()).size()
                        != command.requestedMealSlots().size()
                || command.defaultServings() == null
                || command.defaultServings().signum() <= 0
                || command.defaultServings().compareTo(new BigDecimal("50")) > 0
                || command.defaultServings().scale() > 2
                || (command.maxMinutesPerMeal() != null
                && (command.maxMinutesPerMeal() < 1
                || command.maxMinutesPerMeal() > MealPlanningContractLimits.MAX_MINUTES_PER_MEAL))) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT);
        }
        try { command.startDate().plusDays(command.days() - 1L); }
        catch (DateTimeException exception) {
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.INVALID_GENERATION_INPUT, exception);
        }
    }
}
