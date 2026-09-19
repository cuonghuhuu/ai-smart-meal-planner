package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;

/** All deterministic P11 score dimensions, normalized to [0, 1]. */
public record RecommendationScore(
        BigDecimal pantryCoverage,
        BigDecimal nutritionFit,
        BigDecimal expiryUrgency,
        BigDecimal preferenceMatch,
        BigDecimal variety,
        BigDecimal effortFit,
        BigDecimal dislikePenalty,
        BigDecimal totalScore) {
}
