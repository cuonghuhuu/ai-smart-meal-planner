package com.smartmealplanner.nutrition.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Period;
import java.util.List;

/**
 * Pure deterministic implementation of MIFFLIN_ST_JEOR_V1.
 */
public final class NutritionTargetCalculator {

    public static final String CALCULATION_METHOD =
            "MIFFLIN_ST_JEOR_V1";

    public static final int MINIMUM_SUPPORTED_AGE =
            19;

    private static final int TARGET_SCALE =
            2;

    private static final RoundingMode TARGET_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal TEN =
            new BigDecimal("10");

    private static final BigDecimal SIX_POINT_TWO_FIVE =
            new BigDecimal("6.25");

    private static final BigDecimal FIVE =
            new BigDecimal("5");

    private static final BigDecimal MALE_OFFSET =
            new BigDecimal("5");

    private static final BigDecimal FEMALE_OFFSET =
            new BigDecimal("-161");

    private static final BigDecimal CARBOHYDRATE_MIN_PERCENT =
            new BigDecimal("0.45");

    private static final BigDecimal CARBOHYDRATE_MAX_PERCENT =
            new BigDecimal("0.65");

    private static final BigDecimal FAT_MIN_PERCENT =
            new BigDecimal("0.20");

    private static final BigDecimal FAT_MAX_PERCENT =
            new BigDecimal("0.35");

    private static final BigDecimal PROTEIN_MIN_PERCENT =
            new BigDecimal("0.10");

    private static final BigDecimal PROTEIN_MAX_PERCENT =
            new BigDecimal("0.35");

    private static final BigDecimal KCAL_PER_GRAM_CARBOHYDRATE =
            new BigDecimal("4");

    private static final BigDecimal KCAL_PER_GRAM_FAT =
            new BigDecimal("9");

    private static final BigDecimal KCAL_PER_GRAM_PROTEIN =
            new BigDecimal("4");

    public NutritionCalculationResult calculate(
            NutritionCalculationInput input) {

        if (input == null) {
            throw new NutritionCalculationInputException(
                    "input is required");
        }

        int age = Period.between(
                        input.birthDate(),
                        input.effectiveFrom())
                .getYears();

        if (age < MINIMUM_SUPPORTED_AGE) {
            return unsupported(
                    age,
                    NutritionCalculationReason.AGE_BELOW_MINIMUM);
        }

        if (input.sex() != NutritionCalculationSex.MALE
                && input.sex() != NutritionCalculationSex.FEMALE) {

            return unsupported(
                    age,
                    NutritionCalculationReason.SEX_NOT_SUPPORTED);
        }

        BigDecimal rmrKcal = calculateRmr(
                input,
                age);

        BigDecimal maintenanceEnergyKcal = rmrKcal.multiply(
                input.activityFactor());

        if (NutritionCalculationGoals.isBaselineOnly(
                input.nutritionGoalCode())) {

            return new NutritionCalculationResult(
                    CALCULATION_METHOD,
                    NutritionCalculationStatus.BASELINE_ONLY,
                    NutritionCalculationReason.GOAL_ADJUSTMENT_NOT_SUPPORTED,
                    age,
                    rmrKcal,
                    maintenanceEnergyKcal,
                    List.of());
        }

        return new NutritionCalculationResult(
                CALCULATION_METHOD,
                NutritionCalculationStatus.TARGET_AVAILABLE,
                null,
                age,
                rmrKcal,
                maintenanceEnergyKcal,
                calculatedTargets(maintenanceEnergyKcal));
    }

    private static BigDecimal calculateRmr(
            NutritionCalculationInput input,
            int age) {

        BigDecimal rmr = TEN.multiply(input.weightKg())
                .add(SIX_POINT_TWO_FIVE.multiply(input.heightCm()))
                .subtract(FIVE.multiply(BigDecimal.valueOf(age)));

        return rmr.add(
                input.sex() == NutritionCalculationSex.MALE
                        ? MALE_OFFSET
                        : FEMALE_OFFSET);
    }

    private static List<CalculatedNutrientTarget> calculatedTargets(
            BigDecimal maintenanceEnergyKcal) {

        return List.of(
                new CalculatedNutrientTarget(
                        "ENERGY",
                        roundTarget(maintenanceEnergyKcal),
                        null,
                        null,
                        false),
                new CalculatedNutrientTarget(
                        "CARBOHYDRATE",
                        null,
                        roundMacro(
                                maintenanceEnergyKcal,
                                CARBOHYDRATE_MIN_PERCENT,
                                KCAL_PER_GRAM_CARBOHYDRATE),
                        roundMacro(
                                maintenanceEnergyKcal,
                                CARBOHYDRATE_MAX_PERCENT,
                                KCAL_PER_GRAM_CARBOHYDRATE),
                        false),
                new CalculatedNutrientTarget(
                        "FAT_TOTAL",
                        null,
                        roundMacro(
                                maintenanceEnergyKcal,
                                FAT_MIN_PERCENT,
                                KCAL_PER_GRAM_FAT),
                        roundMacro(
                                maintenanceEnergyKcal,
                                FAT_MAX_PERCENT,
                                KCAL_PER_GRAM_FAT),
                        false),
                new CalculatedNutrientTarget(
                        "PROTEIN",
                        null,
                        roundMacro(
                                maintenanceEnergyKcal,
                                PROTEIN_MIN_PERCENT,
                                KCAL_PER_GRAM_PROTEIN),
                        roundMacro(
                                maintenanceEnergyKcal,
                                PROTEIN_MAX_PERCENT,
                                KCAL_PER_GRAM_PROTEIN),
                        false));
    }

    private static BigDecimal roundTarget(
            BigDecimal value) {

        return value.setScale(
                TARGET_SCALE,
                TARGET_ROUNDING);
    }

    private static BigDecimal roundMacro(
            BigDecimal maintenanceEnergyKcal,
            BigDecimal energyPercent,
            BigDecimal kcalPerGram) {

        return maintenanceEnergyKcal
                .multiply(energyPercent)
                .divide(
                        kcalPerGram,
                        TARGET_SCALE,
                        TARGET_ROUNDING);
    }

    private static NutritionCalculationResult unsupported(
            int age,
            NutritionCalculationReason reason) {

        return new NutritionCalculationResult(
                CALCULATION_METHOD,
                NutritionCalculationStatus.UNSUPPORTED,
                reason,
                age,
                null,
                null,
                List.of());
    }
}
