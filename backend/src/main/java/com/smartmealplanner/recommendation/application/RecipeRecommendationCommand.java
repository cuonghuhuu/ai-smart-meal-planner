package com.smartmealplanner.recommendation.application;

import java.math.BigDecimal;

import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.MealSlotCode;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractLimits;

/** User-selectable context; all safety and preference state remains server-owned. */
public record RecipeRecommendationCommand(
        MealSlotCode mealSlotCode,
        BigDecimal servings,
        Integer maxMinutes,
        int resultLimit) {

    public RecipeRecommendationCommand {
        if (mealSlotCode == null || servings == null || servings.signum() <= 0
                || servings.compareTo(new BigDecimal("50.00")) > 0
                || servings.scale() > 2
                || maxMinutes != null && (maxMinutes < 1
                || maxMinutes > RecipeRankingContractLimits.MAX_MINUTES)
                || resultLimit < 1
                || resultLimit > RecipeRankingContractLimits.MAX_RESULT_LIMIT) {
            throw new RecipeRecommendationException(
                    RecipeRecommendationFailure.INVALID_INPUT);
        }
    }
}
