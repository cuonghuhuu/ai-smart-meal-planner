package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure deterministic RULE_BASED_V1 score formula. */
public final class MealPlanScoring {
    public static final BigDecimal PANTRY_WEIGHT = new BigDecimal("0.40");
    public static final BigDecimal NUTRITION_WEIGHT = new BigDecimal("0.25");
    public static final BigDecimal EXPIRY_WEIGHT = new BigDecimal("0.10");
    public static final BigDecimal PREFERENCE_WEIGHT = new BigDecimal("0.10");
    public static final BigDecimal VARIETY_WEIGHT = new BigDecimal("0.10");
    public static final BigDecimal EFFORT_WEIGHT = new BigDecimal("0.05");
    public static final BigDecimal DISLIKE_WEIGHT = new BigDecimal("0.20");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int SCORE_SCALE = 4;

    private MealPlanScoring() {
    }

    public static RecommendationScore score(
            BigDecimal pantryCoverage,
            BigDecimal nutritionFit,
            BigDecimal expiryUrgency,
            BigDecimal preferenceMatch,
            BigDecimal variety,
            BigDecimal effortFit,
            BigDecimal dislikePenalty) {

        BigDecimal pantry = bounded(pantryCoverage);
        BigDecimal nutrition = bounded(nutritionFit);
        BigDecimal expiry = bounded(expiryUrgency);
        BigDecimal preference = bounded(preferenceMatch);
        BigDecimal varietyScore = bounded(variety);
        BigDecimal effort = bounded(effortFit);
        BigDecimal dislike = bounded(dislikePenalty);

        BigDecimal total = pantry.multiply(PANTRY_WEIGHT)
                .add(nutrition.multiply(NUTRITION_WEIGHT))
                .add(expiry.multiply(EXPIRY_WEIGHT))
                .add(preference.multiply(PREFERENCE_WEIGHT))
                .add(varietyScore.multiply(VARIETY_WEIGHT))
                .add(effort.multiply(EFFORT_WEIGHT))
                .subtract(dislike.multiply(DISLIKE_WEIGHT));

        return new RecommendationScore(
                rounded(pantry),
                rounded(nutrition),
                rounded(expiry),
                rounded(preference),
                rounded(varietyScore),
                rounded(effort),
                rounded(dislike),
                rounded(total));
    }

    private static BigDecimal bounded(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("score is required");
        }
        return value.max(ZERO).min(ONE);
    }

    private static BigDecimal rounded(BigDecimal value) {
        return value.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
