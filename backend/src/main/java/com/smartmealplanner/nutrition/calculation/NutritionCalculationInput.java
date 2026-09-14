package com.smartmealplanner.nutrition.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persistence-neutral snapshot of the values required by the nutrition
 * calculator.
 */
public record NutritionCalculationInput(
        LocalDate effectiveFrom,
        LocalDate birthDate,
        NutritionCalculationSex sex,
        BigDecimal heightCm,
        BigDecimal weightKg,
        LocalDate weightMeasuredOn,
        BigDecimal activityFactor,
        String nutritionGoalCode) {

    private static final LocalDate EARLIEST_BIRTH_DATE =
            LocalDate.of(1900, 1, 1);

    private static final BigDecimal MIN_HEIGHT_CM =
            new BigDecimal("30");

    private static final BigDecimal MAX_HEIGHT_CM =
            new BigDecimal("300");

    private static final BigDecimal MIN_WEIGHT_KG =
            new BigDecimal("2");

    private static final BigDecimal MAX_WEIGHT_KG =
            new BigDecimal("700");

    private static final BigDecimal MIN_ACTIVITY_FACTOR =
            new BigDecimal("1.000");

    private static final BigDecimal MAX_ACTIVITY_FACTOR =
            new BigDecimal("3.000");

    public NutritionCalculationInput {
        require(
                effectiveFrom,
                "effectiveFrom");
        require(
                birthDate,
                "birthDate");
        require(
                sex,
                "sex");
        require(
                heightCm,
                "heightCm");
        require(
                weightKg,
                "weightKg");
        require(
                weightMeasuredOn,
                "weightMeasuredOn");
        require(
                activityFactor,
                "activityFactor");

        if (birthDate.isAfter(effectiveFrom)) {
            throw new NutritionCalculationInputException(
                    "birthDate must not be after effectiveFrom");
        }

        if (!birthDate.isAfter(EARLIEST_BIRTH_DATE)) {
            throw new NutritionCalculationInputException(
                    "birthDate must be after 1900-01-01");
        }

        if (weightMeasuredOn.isAfter(effectiveFrom)) {
            throw new NutritionCalculationInputException(
                    "weightMeasuredOn must not be after effectiveFrom");
        }

        if (heightCm.compareTo(MIN_HEIGHT_CM) <= 0
                || heightCm.compareTo(MAX_HEIGHT_CM) >= 0) {

            throw new NutritionCalculationInputException(
                    "heightCm must be between 30 and 300 cm");
        }

        if (weightKg.compareTo(MIN_WEIGHT_KG) <= 0
                || weightKg.compareTo(MAX_WEIGHT_KG) >= 0) {

            throw new NutritionCalculationInputException(
                    "weightKg must be between 2 and 700 kg");
        }

        if (activityFactor.compareTo(MIN_ACTIVITY_FACTOR) < 0
                || activityFactor.compareTo(MAX_ACTIVITY_FACTOR) > 0) {

            throw new NutritionCalculationInputException(
                    "activityFactor must be between 1.000 and 3.000");
        }

        nutritionGoalCode = requireCode(
                nutritionGoalCode);

        if (!NutritionCalculationGoals.isKnown(
                nutritionGoalCode)) {

            throw new NutritionCalculationInputException(
                    "Unknown nutrition goal code: "
                            + nutritionGoalCode);
        }
    }

    private static <T> T require(
            T value,
            String fieldName) {

        if (value == null) {
            throw new NutritionCalculationInputException(
                    fieldName + " is required");
        }

        return value;
    }

    private static String requireCode(
            String code) {

        if (code == null || code.isBlank()) {
            throw new NutritionCalculationInputException(
                    "nutritionGoalCode is required");
        }

        return code.trim();
    }
}
