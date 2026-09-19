package com.smartmealplanner.mealplanning.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.MealPlanConsumptionStatus;
import com.smartmealplanner.mealplanning.MealPlanEntryProvenance;
import com.smartmealplanner.mealplanning.MealPlanStatus;

/** Public surrogate-ID-free Meal Plan representations. */
public final class MealPlanResponse {
    private MealPlanResponse() {
    }

    public record Item(
            UUID publicId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            Integer dayCount,
            Integer mealsPerDayTarget,
            Integer defaultServings,
            MealPlanStatus status,
            LocalDateTime createdAt) {
    }

    public record Detail(
            UUID publicId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            Integer dayCount,
            Integer mealsPerDayTarget,
            Integer defaultServings,
            MealPlanStatus status,
            LocalDateTime acceptedAt,
            LocalDateTime createdAt,
            List<Entry> entries) {
    }

    public record Entry(
            LocalDate planDate,
            String mealSlotCode,
            String mealSlotDisplayName,
            Integer position,
            UUID recipePublicId,
            String recipeTitle,
            BigDecimal servings,
            MealPlanEntryProvenance provenance,
            MealPlanConsumptionStatus consumptionStatus,
            BigDecimal recommendationTotalScore,
            String recommendationExplanation) {
    }

    public record Generation(
            Detail plan,
            List<String> warnings) {
    }
}
