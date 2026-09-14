package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationReason;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationSex;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationStatus;
import com.smartmealplanner.nutrition.calculation.NutritionTargetCalculator;
import com.smartmealplanner.nutrition.lifecycle.NutritionTargetCreateCommand;
import com.smartmealplanner.nutrition.lifecycle.NutritionTargetLifecycleException;
import com.smartmealplanner.nutrition.lifecycle.NutritionTargetLifecycleFailure;
import com.smartmealplanner.nutrition.lifecycle.NutritionTargetLifecycleResult;
import com.smartmealplanner.nutrition.lifecycle.NutritionTargetLifecycleService;
import com.smartmealplanner.nutrition.lifecycle.NutritionTargetValueDraft;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;
import com.smartmealplanner.profile.application.NutritionProfileQueryService;
import com.smartmealplanner.profile.application.NutritionProfileSnapshot;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionTargetApplicationServiceTest {

    private static final UUID PUBLIC_ID = UUID.randomUUID();

    private static final Long INTERNAL_ID = 42L;

    private static final LocalDate EFFECTIVE_FROM =
            LocalDate.of(2026, 9, 15);

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private NutritionProfileQueryService profileQueryService;

    @Mock
    private NutritionTargetLifecycleService lifecycleService;

    private NutritionTargetApplicationService service;

    @BeforeEach
    void setUp() {
        service = new NutritionTargetApplicationService(
                currentUserService,
                profileQueryService,
                lifecycleService,
                new NutritionTargetCalculator());
    }

    @Test
    void previewUsesAuthoritativeSnapshotAndDoesNotPersist() {
        stubProfile(completeProfile("MALE", "MAINTAIN"));

        NutritionCalculationPreviewResult result = service
                .previewCalculation(PUBLIC_ID, EFFECTIVE_FROM);

        assertThat(result.effectiveFrom()).isEqualTo(EFFECTIVE_FROM);
        assertThat(result.calculationMethod())
                .isEqualTo(NutritionTargetCalculator.CALCULATION_METHOD);
        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);
        assertThat(result.reason()).isNull();
        assertThat(result.input().sex())
                .isEqualTo(NutritionCalculationSex.MALE);
        assertThat(result.input().weightKg())
                .isEqualByComparingTo("80.00");
        assertThat(result.input().weightMeasuredOn())
                .isEqualTo(EFFECTIVE_FROM.minusDays(1));
        assertThat(result.age()).isEqualTo(30);
        assertThat(result.rmrKcal()).isEqualByComparingTo("1780");
        assertThat(result.maintenanceEnergyKcal())
                .isEqualByComparingTo("2759.000");
        assertThat(result.nutrientTargets()).hasSize(4);

        verify(lifecycleService, never()).createTarget(any());
        verify(profileQueryService)
                .findNutritionSnapshot(INTERNAL_ID, EFFECTIVE_FROM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EAT_HEALTHIER", "REDUCE_WASTE"})
    void nonWeightMaintenanceGoalsRemainTargetAvailable(
            String nutritionGoalCode) {
        stubProfile(completeProfile("FEMALE", nutritionGoalCode));

        NutritionCalculationPreviewResult result = service
                .previewCalculation(PUBLIC_ID, EFFECTIVE_FROM);

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);
        assertThat(result.nutrientTargets()).hasSize(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOSE_WEIGHT", "GAIN_WEIGHT", "BUILD_MUSCLE"})
    void weightChangeGoalsExposeOnlyATheoreticalBaseline(
            String nutritionGoalCode) {
        stubProfile(completeProfile("MALE", nutritionGoalCode));

        NutritionCalculationPreviewResult result = service
                .previewCalculation(PUBLIC_ID, EFFECTIVE_FROM);

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.BASELINE_ONLY);
        assertThat(result.reason())
                .isEqualTo(NutritionCalculationReason
                        .GOAL_ADJUSTMENT_NOT_SUPPORTED);
        assertThat(result.rmrKcal()).isEqualByComparingTo("1780");
        assertThat(result.maintenanceEnergyKcal())
                .isEqualByComparingTo("2759.000");
        assertThat(result.nutrientTargets()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = NutritionCalculationSex.class,
            names = {"OTHER", "PREFER_NOT_TO_SAY"})
    void unsupportedProfileSexIsReturnedAsUnsupported(
            NutritionCalculationSex sex) {
        stubProfile(completeProfile(sex.name(), "MAINTAIN"));

        NutritionCalculationPreviewResult result = service
                .previewCalculation(PUBLIC_ID, EFFECTIVE_FROM);

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.UNSUPPORTED);
        assertThat(result.reason())
                .isEqualTo(NutritionCalculationReason.SEX_NOT_SUPPORTED);
        assertThat(result.age()).isEqualTo(30);
        assertThat(result.nutrientTargets()).isEmpty();
    }

    @Test
    void underNineteenProfileIsReturnedAsUnsupported() {
        stubProfile(completeProfile(
                "MALE",
                "MAINTAIN",
                LocalDate.of(2007, 9, 16)));

        NutritionCalculationPreviewResult result = service
                .previewCalculation(PUBLIC_ID, EFFECTIVE_FROM);

        assertThat(result.status())
                .isEqualTo(NutritionCalculationStatus.UNSUPPORTED);
        assertThat(result.reason())
                .isEqualTo(NutritionCalculationReason.AGE_BELOW_MINIMUM);
        assertThat(result.age()).isEqualTo(18);
    }

    @Test
    void profileAbsenceIsAStructuredApplicationFailure() {
        stubIdentity();
        when(profileQueryService.findNutritionSnapshot(
                INTERNAL_ID,
                EFFECTIVE_FROM)).thenReturn(Optional.empty());

        assertFailure(
                () -> service.previewCalculation(PUBLIC_ID, EFFECTIVE_FROM),
                NutritionApplicationFailure.PROFILE_NOT_FOUND);
    }

    @Test
    void missingHistoricalMeasurementIsDistinctFromMissingProfile() {
        stubProfile(completeProfile(
                "MALE",
                "MAINTAIN",
                LocalDate.of(1996, 9, 15),
                null,
                null));

        assertFailure(
                () -> service.previewCalculation(PUBLIC_ID, EFFECTIVE_FROM),
                NutritionApplicationFailure.MEASUREMENT_NOT_FOUND);
    }

    @Test
    void missingCalculationInputPreventsCalculatedPersistence() {
        stubProfile(completeProfile(
                "MALE",
                "MAINTAIN",
                LocalDate.of(1996, 9, 15),
                null,
                null));

        assertFailure(
                () -> service.createCalculatedTarget(
                        PUBLIC_ID,
                        EFFECTIVE_FROM),
                NutritionApplicationFailure.MEASUREMENT_NOT_FOUND);

        verify(lifecycleService, never()).createTarget(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"birthDate", "sexCode", "heightCm",
            "activityLevelCode", "activityFactor", "nutritionGoalCode"})
    void missingCalculationContextIsRejectedWithoutDefaults(
            String missingField) {
        NutritionProfileSnapshot complete = completeProfile(
                "MALE",
                "MAINTAIN");
        NutritionProfileSnapshot incomplete = switch (missingField) {
            case "birthDate" -> new NutritionProfileSnapshot(
                    null,
                    complete.sexCode(),
                    complete.heightCm(),
                    complete.activityLevelId(),
                    complete.activityLevelCode(),
                    complete.activityFactor(),
                    complete.nutritionGoalId(),
                    complete.nutritionGoalCode(),
                    complete.selectedWeightKg(),
                    complete.selectedWeightMeasuredOn());
            case "heightCm" -> new NutritionProfileSnapshot(
                    complete.birthDate(),
                    complete.sexCode(),
                    null,
                    complete.activityLevelId(),
                    complete.activityLevelCode(),
                    complete.activityFactor(),
                    complete.nutritionGoalId(),
                    complete.nutritionGoalCode(),
                    complete.selectedWeightKg(),
                    complete.selectedWeightMeasuredOn());
            case "sexCode" -> new NutritionProfileSnapshot(
                    complete.birthDate(),
                    null,
                    complete.heightCm(),
                    complete.activityLevelId(),
                    complete.activityLevelCode(),
                    complete.activityFactor(),
                    complete.nutritionGoalId(),
                    complete.nutritionGoalCode(),
                    complete.selectedWeightKg(),
                    complete.selectedWeightMeasuredOn());
            case "activityLevelCode" -> new NutritionProfileSnapshot(
                    complete.birthDate(),
                    complete.sexCode(),
                    complete.heightCm(),
                    complete.activityLevelId(),
                    null,
                    complete.activityFactor(),
                    complete.nutritionGoalId(),
                    complete.nutritionGoalCode(),
                    complete.selectedWeightKg(),
                    complete.selectedWeightMeasuredOn());
            case "activityFactor" -> new NutritionProfileSnapshot(
                    complete.birthDate(),
                    complete.sexCode(),
                    complete.heightCm(),
                    complete.activityLevelId(),
                    complete.activityLevelCode(),
                    null,
                    complete.nutritionGoalId(),
                    complete.nutritionGoalCode(),
                    complete.selectedWeightKg(),
                    complete.selectedWeightMeasuredOn());
            case "nutritionGoalCode" -> new NutritionProfileSnapshot(
                    complete.birthDate(),
                    complete.sexCode(),
                    complete.heightCm(),
                    complete.activityLevelId(),
                    complete.activityLevelCode(),
                    complete.activityFactor(),
                    complete.nutritionGoalId(),
                    null,
                    complete.selectedWeightKg(),
                    complete.selectedWeightMeasuredOn());
            default -> throw new IllegalStateException(
                    "Unexpected test field: " + missingField);
        };
        stubProfile(incomplete);

        assertFailure(
                () -> service.previewCalculation(PUBLIC_ID, EFFECTIVE_FROM),
                NutritionApplicationFailure.MISSING_CALCULATION_INPUT);
    }

    @Test
    void unknownSexCodeIsNotSilentlyMappedToAFormula() {
        stubProfile(completeProfile("UNKNOWN", "MAINTAIN"));

        assertFailure(
                () -> service.previewCalculation(PUBLIC_ID, EFFECTIVE_FROM),
                NutritionApplicationFailure.CALCULATION_NOT_SUPPORTED);
    }

    @Test
    void nullEffectiveDateIsRejectedBeforeIdentityLookup() {
        assertFailure(
                () -> service.previewCalculation(PUBLIC_ID, null),
                NutritionApplicationFailure.INVALID_REQUEST);

        verifyNoInteractions(currentUserService);
    }

    @Test
    void calculatedPersistenceRerunsCalculationAndPersistsServerValues() {
        stubProfile(completeProfile("MALE", "MAINTAIN"));
        when(lifecycleService.createTarget(any()))
                .thenReturn(new NutritionTargetLifecycleResult(
                        500L,
                        EFFECTIVE_FROM,
                        null));

        NutritionTargetApplicationResult result = service
                .createCalculatedTarget(PUBLIC_ID, EFFECTIVE_FROM);

        assertThat(result.effectiveFrom()).isEqualTo(EFFECTIVE_FROM);
        assertThat(result.effectiveTo()).isNull();
        assertThat(result.origin())
                .isEqualTo(NutritionTargetOrigin.CALCULATED);
        assertThat(result.calculationMethod())
                .isEqualTo(NutritionTargetCalculator.CALCULATION_METHOD);
        assertThat(result.calculationStatus())
                .isEqualTo(NutritionCalculationStatus.TARGET_AVAILABLE);

        ArgumentCaptor<NutritionTargetCreateCommand> captor =
                ArgumentCaptor.forClass(NutritionTargetCreateCommand.class);
        verify(lifecycleService).createTarget(captor.capture());

        NutritionTargetCreateCommand command = captor.getValue();
        assertThat(command.userId()).isEqualTo(INTERNAL_ID);
        assertThat(command.origin())
                .isEqualTo(NutritionTargetOrigin.CALCULATED);
        assertThat(command.activityLevelId()).isEqualTo(7L);
        assertThat(command.nutritionGoalId()).isEqualTo(9L);
        assertThat(command.calculationMethod())
                .isEqualTo("MIFFLIN_ST_JEOR_V1");
        assertThat(command.note()).isNull();
        assertThat(command.nutrientValues()).hasSize(4);
        assertThat(value(command, "ENERGY").targetAmount())
                .isEqualByComparingTo("2759.00");
        assertThat(value(command, "CARBOHYDRATE").minAmount())
                .isEqualByComparingTo("310.39");
        assertThat(value(command, "FAT_TOTAL").maxAmount())
                .isEqualByComparingTo("107.29");
        assertThat(value(command, "PROTEIN").maxAmount())
                .isEqualByComparingTo("241.41");

        assertThat(NutritionTargetApplicationResult.class
                .getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("targetId", "userId");
    }

    @Test
    void calculatedPersistenceDoesNotReuseAnEarlierPreview() {
        stubIdentity();
        NutritionProfileSnapshot previewProfile = completeProfile(
                "MALE",
                "MAINTAIN",
                LocalDate.of(1996, 9, 15),
                new BigDecimal("80.00"),
                EFFECTIVE_FROM.minusDays(1));
        NutritionProfileSnapshot persistenceProfile = completeProfile(
                "MALE",
                "MAINTAIN",
                LocalDate.of(1996, 9, 15),
                new BigDecimal("90.00"),
                EFFECTIVE_FROM);
        when(profileQueryService.findNutritionSnapshot(
                INTERNAL_ID,
                EFFECTIVE_FROM))
                .thenReturn(Optional.of(previewProfile))
                .thenReturn(Optional.of(persistenceProfile));
        when(lifecycleService.createTarget(any()))
                .thenReturn(new NutritionTargetLifecycleResult(
                        501L,
                        EFFECTIVE_FROM,
                        null));

        service.previewCalculation(PUBLIC_ID, EFFECTIVE_FROM);
        service.createCalculatedTarget(PUBLIC_ID, EFFECTIVE_FROM);

        ArgumentCaptor<NutritionTargetCreateCommand> captor =
                ArgumentCaptor.forClass(NutritionTargetCreateCommand.class);
        verify(lifecycleService).createTarget(captor.capture());
        assertThat(value(captor.getValue(), "ENERGY").targetAmount())
                .isEqualByComparingTo("2914.00");
        verify(profileQueryService, times(2))
                .findNutritionSnapshot(INTERNAL_ID, EFFECTIVE_FROM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOSE_WEIGHT", "GAIN_WEIGHT", "BUILD_MUSCLE"})
    void baselineOnlyCalculationCannotBePersisted(
            String nutritionGoalCode) {
        stubProfile(completeProfile("MALE", nutritionGoalCode));

        assertFailure(
                () -> service.createCalculatedTarget(
                        PUBLIC_ID,
                        EFFECTIVE_FROM),
                NutritionApplicationFailure.GOAL_ADJUSTMENT_NOT_SUPPORTED);

        verify(lifecycleService, never()).createTarget(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = NutritionCalculationSex.class,
            names = {"OTHER", "PREFER_NOT_TO_SAY"})
    void unsupportedCalculationCannotBePersisted(
            NutritionCalculationSex sex) {
        stubProfile(completeProfile(sex.name(), "MAINTAIN"));

        assertFailure(
                () -> service.createCalculatedTarget(
                        PUBLIC_ID,
                        EFFECTIVE_FROM),
                NutritionApplicationFailure.CALCULATION_NOT_SUPPORTED);

        verify(lifecycleService, never()).createTarget(any());
    }

    @Test
    void userDefinedPersistenceDoesNotRequireAutomaticCalculationContext() {
        stubIdentity();
        when(lifecycleService.createTarget(any()))
                .thenReturn(new NutritionTargetLifecycleResult(
                        700L,
                        EFFECTIVE_FROM,
                        null));
        UserDefinedNutritionTargetCommand command =
                new UserDefinedNutritionTargetCommand(
                        EFFECTIVE_FROM,
                        List.of(
                                new UserDefinedNutritionValueCommand(
                                        "ENERGY",
                                        new BigDecimal("2000.00"),
                                        null,
                                        null,
                                        false),
                                new UserDefinedNutritionValueCommand(
                                        "PROTEIN",
                                        null,
                                        new BigDecimal("80.00"),
                                        new BigDecimal("120.00"),
                                        true)));

        NutritionTargetApplicationResult result = service
                .createUserDefinedTarget(PUBLIC_ID, command);

        assertThat(result.origin())
                .isEqualTo(NutritionTargetOrigin.USER_DEFINED);
        assertThat(result.calculationMethod()).isNull();
        assertThat(result.calculationStatus()).isNull();
        assertThat(result.calculationReason()).isNull();

        ArgumentCaptor<NutritionTargetCreateCommand> captor =
                ArgumentCaptor.forClass(NutritionTargetCreateCommand.class);
        verify(lifecycleService).createTarget(captor.capture());
        NutritionTargetCreateCommand lifecycleCommand = captor.getValue();
        assertThat(lifecycleCommand.userId()).isEqualTo(INTERNAL_ID);
        assertThat(lifecycleCommand.origin())
                .isEqualTo(NutritionTargetOrigin.USER_DEFINED);
        assertThat(lifecycleCommand.activityLevelId()).isNull();
        assertThat(lifecycleCommand.nutritionGoalId()).isNull();
        assertThat(lifecycleCommand.calculationMethod()).isNull();
        assertThat(lifecycleCommand.nutrientValues()).hasSize(2);
        verifyNoInteractions(profileQueryService);
    }

    @Test
    void invalidUserDefinedValueIsRejectedBeforeLifecyclePersistence() {
        stubIdentity();
        UserDefinedNutritionTargetCommand command =
                new UserDefinedNutritionTargetCommand(
                        EFFECTIVE_FROM,
                        List.of(new UserDefinedNutritionValueCommand(
                                "ENERGY",
                                null,
                                new BigDecimal("10.00"),
                                new BigDecimal("9.00"),
                                false)));

        assertFailure(
                () -> service.createUserDefinedTarget(PUBLIC_ID, command),
                NutritionApplicationFailure.INVALID_REQUEST);

        verify(lifecycleService, never()).createTarget(any());
        verifyNoInteractions(profileQueryService);
    }

    @Test
    void lifecycleFailuresAreTranslatedToStructuredApplicationFailures() {
        stubIdentity();
        when(lifecycleService.createTarget(any()))
                .thenThrow(new NutritionTargetLifecycleException(
                        NutritionTargetLifecycleFailure.UNKNOWN_NUTRIENT_CODE,
                        "persistence detail must not be the application contract"));

        UserDefinedNutritionTargetCommand command =
                new UserDefinedNutritionTargetCommand(
                        EFFECTIVE_FROM,
                        List.of(new UserDefinedNutritionValueCommand(
                                "NOT_A_REFERENCE",
                                BigDecimal.ONE,
                                null,
                                null,
                                false)));

        assertFailure(
                () -> service.createUserDefinedTarget(PUBLIC_ID, command),
                NutritionApplicationFailure.UNKNOWN_NUTRIENT_CODE);
    }

    private void stubProfile(NutritionProfileSnapshot profile) {
        stubIdentity();
        when(profileQueryService.findNutritionSnapshot(
                INTERNAL_ID,
                EFFECTIVE_FROM)).thenReturn(Optional.of(profile));
    }

    private void stubIdentity() {
        when(currentUserService.getIdentity(PUBLIC_ID))
                .thenReturn(new CurrentUserIdentity(
                        INTERNAL_ID,
                        PUBLIC_ID,
                        "UTC"));
    }

    private static NutritionProfileSnapshot completeProfile(
            String sexCode,
            String nutritionGoalCode) {

        return completeProfile(
                sexCode,
                nutritionGoalCode,
                LocalDate.of(1996, 9, 15));
    }

    private static NutritionProfileSnapshot completeProfile(
            String sexCode,
            String nutritionGoalCode,
            LocalDate birthDate) {

        return completeProfile(
                sexCode,
                nutritionGoalCode,
                birthDate,
                new BigDecimal("80.00"),
                EFFECTIVE_FROM.minusDays(1));
    }

    private static NutritionProfileSnapshot completeProfile(
            String sexCode,
            String nutritionGoalCode,
            LocalDate birthDate,
            BigDecimal weightKg,
            LocalDate weightMeasuredOn) {

        return new NutritionProfileSnapshot(
                birthDate,
                sexCode,
                new BigDecimal("180.00"),
                7L,
                "MODERATE",
                new BigDecimal("1.550"),
                9L,
                nutritionGoalCode,
                weightKg,
                weightMeasuredOn);
    }

    private static NutritionTargetValueDraft value(
            NutritionTargetCreateCommand command,
            String nutrientCode) {

        return command.nutrientValues().stream()
                .filter(value -> nutrientCode.equals(value.nutrientCode()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertFailure(
            ThrowingCallable operation,
            NutritionApplicationFailure expectedFailure) {

        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(
                        NutritionApplicationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(expectedFailure));
    }
}
