package com.smartmealplanner.nutrition.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.nutrition.calculation.CalculatedNutrientTarget;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationInput;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationInputException;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationReason;
import com.smartmealplanner.nutrition.calculation.NutritionCalculationResult;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestrates authenticated profile snapshots, deterministic calculation, and
 * prepared-target lifecycle persistence.
 *
 * <p>This class intentionally has no transaction annotation. Read operations
 * finish in their owning services before a calculated or user-defined write
 * delegates to P6.4's SERIALIZABLE transaction.</p>
 */
@Service
public class NutritionTargetApplicationService {

    private final CurrentUserService currentUserService;
    private final NutritionProfileQueryService profileQueryService;
    private final NutritionTargetLifecycleService lifecycleService;
    private final NutritionTargetCalculator calculator;

    @Autowired
    public NutritionTargetApplicationService(
            CurrentUserService currentUserService,
            NutritionProfileQueryService profileQueryService,
            NutritionTargetLifecycleService lifecycleService) {

        this(
                currentUserService,
                profileQueryService,
                lifecycleService,
                new NutritionTargetCalculator());
    }

    public NutritionTargetApplicationService(
            CurrentUserService currentUserService,
            NutritionProfileQueryService profileQueryService,
            NutritionTargetLifecycleService lifecycleService,
            NutritionTargetCalculator calculator) {

        this.currentUserService = currentUserService;
        this.profileQueryService = profileQueryService;
        this.lifecycleService = lifecycleService;
        this.calculator = calculator;
    }

    /**
     * Calculates a read-only preview from authoritative current profile data.
     */
    public NutritionCalculationPreviewResult previewCalculation(
            UUID authenticatedPublicId,
            LocalDate effectiveFrom) {

        CalculationContext context = calculationContext(
                authenticatedPublicId,
                effectiveFrom);
        NutritionCalculationResult result = calculator.calculate(
                context.input());

        return new NutritionCalculationPreviewResult(
                effectiveFrom,
                result.calculationMethod(),
                result.status(),
                result.reason(),
                context.inputSnapshot(),
                result.age(),
                result.rmrKcal(),
                result.maintenanceEnergyKcal(),
                result.nutrientTargets());
    }

    /**
     * Recalculates authoritative inputs and persists a calculated target only
     * when the calculator returns a persistable target set.
     */
    public NutritionTargetApplicationResult createCalculatedTarget(
            UUID authenticatedPublicId,
            LocalDate effectiveFrom) {

        CalculationContext context = calculationContext(
                authenticatedPublicId,
                effectiveFrom);
        NutritionCalculationResult calculation = calculator.calculate(
                context.input());

        ensureCalculatedPersistenceSupported(calculation);
        requireCalculatedReferenceIds(context.profile());

        List<NutritionTargetValueDraft> values = calculation
                .nutrientTargets()
                .stream()
                .map(NutritionTargetApplicationService::toValueDraft)
                .toList();

        NutritionTargetCreateCommand command =
                new NutritionTargetCreateCommand(
                        context.identity().internalId(),
                        effectiveFrom,
                        NutritionTargetOrigin.CALCULATED,
                        context.profile().activityLevelId(),
                        context.profile().nutritionGoalId(),
                        calculation.calculationMethod(),
                        null,
                        values);

        NutritionTargetLifecycleResult persisted = persist(
                command);

        return new NutritionTargetApplicationResult(
                persisted.effectiveFrom(),
                persisted.effectiveTo(),
                NutritionTargetOrigin.CALCULATED,
                calculation.calculationMethod(),
                calculation.status(),
                calculation.reason());
    }

    /**
     * Persists explicit user-defined values without requiring automatic
     * calculation inputs.
     */
    public NutritionTargetApplicationResult createUserDefinedTarget(
            UUID authenticatedPublicId,
            UserDefinedNutritionTargetCommand command) {

        if (command == null) {
            throw NutritionApplicationException.invalidRequest(
                    "user-defined target command is required");
        }

        CurrentUserIdentity identity = currentUserService.getIdentity(
                authenticatedPublicId);

        NutritionTargetLifecycleResult persisted;
        try {
            List<NutritionTargetValueDraft> values = command
                    .nutrientValues()
                    .stream()
                    .map(NutritionTargetApplicationService::toValueDraft)
                    .toList();

            NutritionTargetCreateCommand lifecycleCommand =
                    new NutritionTargetCreateCommand(
                            identity.internalId(),
                            command.effectiveFrom(),
                            NutritionTargetOrigin.USER_DEFINED,
                            null,
                            null,
                            null,
                            null,
                            values);

            persisted = persist(lifecycleCommand);
        } catch (NutritionTargetLifecycleException exception) {
            throw translateLifecycleFailure(exception);
        }

        return new NutritionTargetApplicationResult(
                persisted.effectiveFrom(),
                persisted.effectiveTo(),
                NutritionTargetOrigin.USER_DEFINED,
                null,
                null,
                null);
    }

    private CalculationContext calculationContext(
            UUID authenticatedPublicId,
            LocalDate effectiveFrom) {

        requireEffectiveFrom(effectiveFrom);

        CurrentUserIdentity identity = currentUserService.getIdentity(
                authenticatedPublicId);

        NutritionProfileSnapshot profile = profileQueryService
                .findNutritionSnapshot(
                        identity.internalId(),
                        effectiveFrom)
                .orElseThrow(() -> new NutritionApplicationException(
                        NutritionApplicationFailure.PROFILE_NOT_FOUND,
                        "Nutrition profile is not available"));

        NutritionCalculationSex sex = mapSex(profile.sexCode());

        if (profile.selectedWeightKg() == null
                || profile.selectedWeightMeasuredOn() == null) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.MEASUREMENT_NOT_FOUND,
                    "No eligible body measurement is available");
        }

        if (profile.birthDate() == null
                || profile.heightCm() == null
                || isBlank(profile.activityLevelCode())
                || profile.activityFactor() == null
                || isBlank(profile.nutritionGoalCode())) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.MISSING_CALCULATION_INPUT,
                    "Required calculation input is missing");
        }

        NutritionCalculationInput input;
        try {
            input = new NutritionCalculationInput(
                    effectiveFrom,
                    profile.birthDate(),
                    sex,
                    profile.heightCm(),
                    profile.selectedWeightKg(),
                    profile.selectedWeightMeasuredOn(),
                    profile.activityFactor(),
                    profile.nutritionGoalCode());
        } catch (NutritionCalculationInputException exception) {
            throw new NutritionApplicationException(
                    NutritionApplicationFailure.MISSING_CALCULATION_INPUT,
                    "Calculation input is invalid",
                    exception);
        }

        NutritionCalculationInputSnapshot inputSnapshot =
                new NutritionCalculationInputSnapshot(
                        profile.birthDate(),
                        sex,
                        profile.heightCm(),
                        profile.selectedWeightKg(),
                        profile.selectedWeightMeasuredOn(),
                        profile.activityLevelCode(),
                        profile.activityFactor(),
                        profile.nutritionGoalCode());

        return new CalculationContext(
                identity,
                profile,
                input,
                inputSnapshot);
    }

    private static void requireCalculatedReferenceIds(
            NutritionProfileSnapshot profile) {

        if (profile.activityLevelId() == null
                || profile.nutritionGoalId() == null) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.MISSING_CALCULATION_INPUT,
                    "Reference snapshot identifiers are missing");
        }
    }

    private static NutritionCalculationSex mapSex(
            String sexCode) {

        if (sexCode == null || sexCode.isBlank()) {
            throw new NutritionApplicationException(
                    NutritionApplicationFailure.MISSING_CALCULATION_INPUT,
                    "Sex is missing");
        }

        return switch (sexCode.trim()) {
            case "MALE" -> NutritionCalculationSex.MALE;
            case "FEMALE" -> NutritionCalculationSex.FEMALE;
            case "OTHER" -> NutritionCalculationSex.OTHER;
            case "PREFER_NOT_TO_SAY" ->
                    NutritionCalculationSex.PREFER_NOT_TO_SAY;
            default -> throw new NutritionApplicationException(
                    NutritionApplicationFailure.CALCULATION_NOT_SUPPORTED,
                    "Sex is not supported by the calculation method");
        };
    }

    private static void ensureCalculatedPersistenceSupported(
            NutritionCalculationResult calculation) {

        if (calculation.status() == NutritionCalculationStatus.BASELINE_ONLY
                || calculation.reason()
                        == NutritionCalculationReason
                                .GOAL_ADJUSTMENT_NOT_SUPPORTED) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure
                            .GOAL_ADJUSTMENT_NOT_SUPPORTED,
                    "Automatic goal adjustment is not supported");
        }

        if (calculation.status() != NutritionCalculationStatus.TARGET_AVAILABLE
                || !calculation.hasPersistableTargetValues()) {

            throw new NutritionApplicationException(
                    NutritionApplicationFailure.CALCULATION_NOT_SUPPORTED,
                    "Automatic target persistence is not supported");
        }
    }

    private NutritionTargetLifecycleResult persist(
            NutritionTargetCreateCommand command) {

        try {
            return lifecycleService.createTarget(command);
        } catch (NutritionTargetLifecycleException exception) {
            throw translateLifecycleFailure(exception);
        }
    }

    private static NutritionApplicationException translateLifecycleFailure(
            NutritionTargetLifecycleException exception) {

        NutritionApplicationFailure failure = switch (
                exception.failure()) {
            case INVALID_COMMAND -> NutritionApplicationFailure.INVALID_REQUEST;
            case DUPLICATE_NUTRIENT_CODE ->
                    NutritionApplicationFailure.DUPLICATE_NUTRIENT_CODE;
            case UNKNOWN_NUTRIENT_CODE ->
                    NutritionApplicationFailure.UNKNOWN_NUTRIENT_CODE;
            case SAME_EFFECTIVE_DATE ->
                    NutritionApplicationFailure.SAME_EFFECTIVE_DATE;
            case CORRUPTED_TIMELINE, OVERLAPPING_TIMELINE ->
                    NutritionApplicationFailure.CORRUPTED_TARGET_TIMELINE;
            case PERSISTENCE_FAILURE ->
                    NutritionApplicationFailure.PERSISTENCE_FAILURE;
        };

        return new NutritionApplicationException(
                failure,
                "Nutrition target operation was rejected",
                exception);
    }

    private static NutritionTargetValueDraft toValueDraft(
            CalculatedNutrientTarget value) {

        return new NutritionTargetValueDraft(
                value.nutrientCode(),
                value.targetAmount(),
                value.minAmount(),
                value.maxAmount(),
                value.hardLimit());
    }

    private static NutritionTargetValueDraft toValueDraft(
            UserDefinedNutritionValueCommand value) {

        return new NutritionTargetValueDraft(
                value.nutrientCode(),
                value.targetAmount(),
                value.minAmount(),
                value.maxAmount(),
                value.hardLimit());
    }

    private static void requireEffectiveFrom(
            LocalDate effectiveFrom) {

        if (effectiveFrom == null) {
            throw NutritionApplicationException.invalidRequest(
                    "effectiveFrom is required");
        }
    }

    private static boolean isBlank(
            String value) {

        return value == null || value.isBlank();
    }

    private record CalculationContext(
            CurrentUserIdentity identity,
            NutritionProfileSnapshot profile,
            NutritionCalculationInput input,
            NutritionCalculationInputSnapshot inputSnapshot) {
    }
}
