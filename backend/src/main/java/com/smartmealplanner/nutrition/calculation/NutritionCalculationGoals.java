package com.smartmealplanner.nutrition.calculation;

import java.util.Set;

final class NutritionCalculationGoals {

    static final String LOSE_WEIGHT = "LOSE_WEIGHT";
    static final String MAINTAIN = "MAINTAIN";
    static final String GAIN_WEIGHT = "GAIN_WEIGHT";
    static final String BUILD_MUSCLE = "BUILD_MUSCLE";
    static final String EAT_HEALTHIER = "EAT_HEALTHIER";
    static final String REDUCE_WASTE = "REDUCE_WASTE";

    private static final Set<String> ALL = Set.of(
            LOSE_WEIGHT,
            MAINTAIN,
            GAIN_WEIGHT,
            BUILD_MUSCLE,
            EAT_HEALTHIER,
            REDUCE_WASTE);

    private static final Set<String> BASELINE_ONLY = Set.of(
            LOSE_WEIGHT,
            GAIN_WEIGHT,
            BUILD_MUSCLE);

    private NutritionCalculationGoals() {
    }

    static boolean isKnown(
            String code) {

        return ALL.contains(code);
    }

    static boolean isBaselineOnly(
            String code) {

        return BASELINE_ONLY.contains(code);
    }
}
