package com.smartmealplanner.nutrition.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionTargetCalculatorTest {

    private static final LocalDate EFFECTIVE_FROM =
            LocalDate.of(2026, 9, 15);

    private final NutritionTargetCalculator calculator =
            new NutritionTargetCalculator();

    @Test
    void calculatesMaleMifflinEnergyAndAdultMacroRanges() {
        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        NutritionCalculationSex.MALE,
                        "MAINTAIN",
                        new BigDecimal("180.00"),
                        new BigDecimal("80.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertThat(result.calculationMethod())
                .isEqualTo(NutritionTargetCalculator.CALCULATION_METHOD);
        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);
        assertThat(result.reason()).isNull();
        assertThat(result.age()).isEqualTo(30);
        assertThat(result.rmrKcal())
                .isEqualByComparingTo("1780");
        assertThat(result.maintenanceEnergyKcal())
                .isEqualByComparingTo("2759.000");

        assertThat(result.nutrientTargets())
                .extracting(CalculatedNutrientTarget::nutrientCode)
                .containsExactly(
                        "ENERGY",
                        "CARBOHYDRATE",
                        "FAT_TOTAL",
                        "PROTEIN");

        CalculatedNutrientTarget energy = target(
                result,
                "ENERGY");
        assertThat(energy.targetAmount())
                .isEqualByComparingTo("2759.00");
        assertThat(energy.targetAmount().scale()).isEqualTo(2);
        assertThat(energy.minAmount()).isNull();
        assertThat(energy.maxAmount()).isNull();
        assertThat(energy.hardLimit()).isFalse();

        assertRange(
                result,
                "CARBOHYDRATE",
                "310.39",
                "448.34");
        assertRange(
                result,
                "FAT_TOTAL",
                "61.31",
                "107.29");
        assertRange(
                result,
                "PROTEIN",
                "68.98",
                "241.41");
    }

    @Test
    void calculatesFemaleMifflinEnergyExactly() {
        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        NutritionCalculationSex.FEMALE,
                        "MAINTAIN",
                        new BigDecimal("165.00"),
                        new BigDecimal("65.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.200")));

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);
        assertThat(result.age()).isEqualTo(30);
        assertThat(result.rmrKcal())
                .isEqualByComparingTo("1370.25");
        assertThat(result.maintenanceEnergyKcal())
                .isEqualByComparingTo("1644.300");
        assertThat(target(result, "ENERGY").targetAmount())
                .isEqualByComparingTo("1644.30");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-14", "2026-09-15"})
    void derivesCompleteYearsAtTheEffectiveDate(String effectiveDate) {
        LocalDate effectiveFrom = LocalDate.parse(effectiveDate);

        NutritionCalculationResult result = calculator.calculate(
                input(
                        effectiveFrom,
                        LocalDate.of(2000, 9, 15),
                        NutritionCalculationSex.MALE,
                        "MAINTAIN",
                        new BigDecimal("170.00"),
                        new BigDecimal("70.00"),
                        effectiveFrom,
                        new BigDecimal("1.200")));

        int expectedAge = effectiveFrom.equals(
                LocalDate.of(2026, 9, 14))
                ? 25
                : 26;

        assertThat(result.age()).isEqualTo(expectedAge);
    }

    @Test
    void supportsExactlyNineteenYearsOfAge() {
        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(2007, 9, 15),
                        NutritionCalculationSex.MALE,
                        "MAINTAIN",
                        new BigDecimal("180.00"),
                        new BigDecimal("80.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertThat(result.age()).isEqualTo(19);
        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);
    }

    @Test
    void reportsAgeBelowNineteenAsUnsupported() {
        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(2007, 9, 16),
                        NutritionCalculationSex.MALE,
                        "MAINTAIN",
                        new BigDecimal("180.00"),
                        new BigDecimal("80.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertUnsupported(
                result,
                18,
                NutritionCalculationReason.AGE_BELOW_MINIMUM);
    }

    @ParameterizedTest
    @EnumSource(
            value = NutritionCalculationSex.class,
            names = {"OTHER", "PREFER_NOT_TO_SAY"})
    void reportsUnsupportedSexWithoutChoosingAFormula(
            NutritionCalculationSex sex) {

        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        sex,
                        "MAINTAIN",
                        new BigDecimal("180.00"),
                        new BigDecimal("80.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertUnsupported(
                result,
                30,
                NutritionCalculationReason.SEX_NOT_SUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAINTAIN", "EAT_HEALTHIER", "REDUCE_WASTE"})
    void maintenanceGoalsProduceTargetAvailable(
            String nutritionGoalCode) {

        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        NutritionCalculationSex.FEMALE,
                        nutritionGoalCode,
                        new BigDecimal("165.00"),
                        new BigDecimal("65.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.200")));

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);
        assertThat(result.reason()).isNull();
        assertThat(result.nutrientTargets()).hasSize(4);
        assertThat(result.hasPersistableTargetValues()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOSE_WEIGHT", "GAIN_WEIGHT", "BUILD_MUSCLE"})
    void weightChangeGoalsProduceBaselineOnlyWithoutAutomaticTargets(
            String nutritionGoalCode) {

        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        NutritionCalculationSex.MALE,
                        nutritionGoalCode,
                        new BigDecimal("180.00"),
                        new BigDecimal("80.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.BASELINE_ONLY);
        assertThat(result.reason())
                .isEqualTo(NutritionCalculationReason.GOAL_ADJUSTMENT_NOT_SUPPORTED);
        assertThat(result.age()).isEqualTo(30);
        assertThat(result.rmrKcal()).isEqualByComparingTo("1780");
        assertThat(result.maintenanceEnergyKcal())
                .isEqualByComparingTo("2759.000");
        assertThat(result.nutrientTargets()).isEmpty();
        assertThat(result.hasPersistableTargetValues()).isFalse();
    }

    @Test
    void derivesMacroRangesFromUnroundedMaintenanceEnergy() {
        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        NutritionCalculationSex.MALE,
                        "MAINTAIN",
                        new BigDecimal("160.12"),
                        new BigDecimal("65.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertThat(result.rmrKcal())
                .isEqualByComparingTo("1505.7500");
        assertThat(result.maintenanceEnergyKcal())
                .isEqualByComparingTo("2333.912500");
        assertThat(target(result, "ENERGY").targetAmount())
                .isEqualByComparingTo("2333.91");

        // 2333.912500 * 45% / 4 = 262.56515625, which rounds to
        // 262.57. Using the rounded energy value 2333.91 would give 262.56.
        assertThat(target(result, "CARBOHYDRATE").minAmount())
                .isEqualByComparingTo("262.57");
        assertThat(target(result, "CARBOHYDRATE").minAmount())
                .isNotEqualByComparingTo("262.56");
    }

    @Test
    void rejectsFutureWeightMeasurementForHistoricalCalculation() {
        assertThatThrownBy(() -> input(
                EFFECTIVE_FROM,
                LocalDate.of(1996, 9, 15),
                NutritionCalculationSex.MALE,
                "MAINTAIN",
                new BigDecimal("180.00"),
                new BigDecimal("80.00"),
                EFFECTIVE_FROM.plusDays(1),
                new BigDecimal("1.550")))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("weightMeasuredOn must not be after effectiveFrom");
    }

    @Test
    void rejectsBirthDateAfterEffectiveDate() {
        assertThatThrownBy(() -> input(
                EFFECTIVE_FROM,
                EFFECTIVE_FROM.plusDays(1),
                NutritionCalculationSex.MALE,
                "MAINTAIN",
                new BigDecimal("180.00"),
                new BigDecimal("80.00"),
                EFFECTIVE_FROM,
                new BigDecimal("1.550")))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("birthDate must not be after effectiveFrom");
    }

    @Test
    void rejectsMissingAndInvalidRequiredInput() {
        assertThatThrownBy(() -> calculator.calculate(null))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("input is required");

        assertThatThrownBy(() -> new NutritionCalculationInput(
                EFFECTIVE_FROM,
                LocalDate.of(1996, 9, 15),
                null,
                new BigDecimal("180.00"),
                new BigDecimal("80.00"),
                EFFECTIVE_FROM,
                new BigDecimal("1.550"),
                "MAINTAIN"))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("sex is required");

        assertThatThrownBy(() -> input(
                EFFECTIVE_FROM,
                LocalDate.of(1996, 9, 15),
                NutritionCalculationSex.MALE,
                "UNKNOWN_GOAL",
                new BigDecimal("180.00"),
                new BigDecimal("80.00"),
                EFFECTIVE_FROM,
                new BigDecimal("1.550")))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("Unknown nutrition goal code: UNKNOWN_GOAL");

        assertThatThrownBy(() -> input(
                EFFECTIVE_FROM,
                LocalDate.of(1996, 9, 15),
                NutritionCalculationSex.MALE,
                "MAINTAIN",
                BigDecimal.ZERO,
                new BigDecimal("80.00"),
                EFFECTIVE_FROM,
                new BigDecimal("1.550")))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("heightCm must be between 30 and 300 cm");

        assertThatThrownBy(() -> input(
                EFFECTIVE_FROM,
                LocalDate.of(1996, 9, 15),
                NutritionCalculationSex.MALE,
                "MAINTAIN",
                new BigDecimal("180.00"),
                BigDecimal.ZERO,
                EFFECTIVE_FROM,
                new BigDecimal("1.550")))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("weightKg must be between 2 and 700 kg");

        assertThatThrownBy(() -> input(
                EFFECTIVE_FROM,
                LocalDate.of(1996, 9, 15),
                NutritionCalculationSex.MALE,
                "MAINTAIN",
                new BigDecimal("180.00"),
                new BigDecimal("80.00"),
                EFFECTIVE_FROM,
                BigDecimal.ZERO))
                .isInstanceOf(NutritionCalculationInputException.class)
                .hasMessage("activityFactor must be between 1.000 and 3.000");
    }

    @Test
    void keepsCalculatedTargetListImmutable() {
        NutritionCalculationResult result = calculator.calculate(
                input(
                        EFFECTIVE_FROM,
                        LocalDate.of(1996, 9, 15),
                        NutritionCalculationSex.MALE,
                        "MAINTAIN",
                        new BigDecimal("180.00"),
                        new BigDecimal("80.00"),
                        EFFECTIVE_FROM,
                        new BigDecimal("1.550")));

        assertThatThrownBy(() -> result.nutrientTargets().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static NutritionCalculationInput input(
            LocalDate effectiveFrom,
            LocalDate birthDate,
            NutritionCalculationSex sex,
            String nutritionGoalCode,
            BigDecimal heightCm,
            BigDecimal weightKg,
            LocalDate weightMeasuredOn,
            BigDecimal activityFactor) {

        return new NutritionCalculationInput(
                effectiveFrom,
                birthDate,
                sex,
                heightCm,
                weightKg,
                weightMeasuredOn,
                activityFactor,
                nutritionGoalCode);
    }

    private static CalculatedNutrientTarget target(
            NutritionCalculationResult result,
            String nutrientCode) {

        return result.nutrientTargets().stream()
                .filter(value -> value.nutrientCode().equals(nutrientCode))
                .findFirst()
                .orElseThrow();
    }

    private static void assertRange(
            NutritionCalculationResult result,
            String nutrientCode,
            String expectedMinimum,
            String expectedMaximum) {

        CalculatedNutrientTarget value = target(
                result,
                nutrientCode);

        assertThat(value.targetAmount()).isNull();
        assertThat(value.minAmount())
                .isEqualByComparingTo(expectedMinimum);
        assertThat(value.maxAmount())
                .isEqualByComparingTo(expectedMaximum);
        assertThat(value.minAmount().scale()).isEqualTo(2);
        assertThat(value.maxAmount().scale()).isEqualTo(2);
        assertThat(value.hardLimit()).isFalse();
    }

    private static void assertUnsupported(
            NutritionCalculationResult result,
            int expectedAge,
            NutritionCalculationReason expectedReason) {

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.UNSUPPORTED);
        assertThat(result.reason()).isEqualTo(expectedReason);
        assertThat(result.age()).isEqualTo(expectedAge);
        assertThat(result.rmrKcal()).isNull();
        assertThat(result.maintenanceEnergyKcal()).isNull();
        assertThat(result.nutrientTargets()).isEmpty();
        assertThat(result.hasPersistableTargetValues()).isFalse();
    }
}
