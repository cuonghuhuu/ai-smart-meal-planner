package com.smartmealplanner.nutrition.lifecycle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes prepared nutrition targets into a user's dated target timeline.
 *
 * <p>This service deliberately receives already-prepared values. Calculation,
 * profile lookup, and authenticated-user resolution belong to a later
 * application workflow.</p>
 */
@Service
public class NutritionTargetLifecycleService {

    private final UserNutritionTargetRepository targetRepository;
    private final UserNutritionTargetValueRepository targetValueRepository;
    private final NutrientRepository nutrientRepository;

    public NutritionTargetLifecycleService(
            UserNutritionTargetRepository targetRepository,
            UserNutritionTargetValueRepository targetValueRepository,
            NutrientRepository nutrientRepository) {

        this.targetRepository = targetRepository;
        this.targetValueRepository = targetValueRepository;
        this.nutrientRepository = nutrientRepository;
    }

    /**
     * Creates one dated target and its values atomically.
     *
     * <p>The existing target query uses a pessimistic write lock. The
     * transaction is additionally scoped to SERIALIZABLE so that an empty
     * timeline is serialized too: MySQL/InnoDB can protect the indexed
     * {@code (user_id, effective_from)} range, including the first write,
     * instead of allowing two concurrent writers to observe no rows.</p>
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public NutritionTargetLifecycleResult createTarget(
            NutritionTargetCreateCommand command) {

        if (command == null) {
            throw NutritionTargetLifecycleException.invalidCommand(
                    "command is required");
        }

        Map<String, Nutrient> nutrientsByCode = resolveNutrients(
                command.nutrientValues());

        List<UserNutritionTarget> timeline = lockedTimeline(
                command.userId());

        rejectSameEffectiveDate(
                timeline,
                command.effectiveFrom());

        UserNutritionTarget predecessor = null;
        UserNutritionTarget successor = null;

        for (UserNutritionTarget target : timeline) {
            if (target.effectiveFrom().isBefore(
                    command.effectiveFrom())) {

                predecessor = target;
            } else {
                successor = target;
                break;
            }
        }

        LocalDate predecessorEffectiveTo = predecessor == null
                ? null
                : derivedPredecessorEnd(
                        predecessor,
                        command.effectiveFrom());

        LocalDate newEffectiveTo = successor == null
                ? null
                : successor.effectiveFrom().minusDays(1);

        verifyResultingTimeline(
                timeline,
                predecessor,
                predecessorEffectiveTo,
                command.effectiveFrom(),
                newEffectiveTo);

        if (predecessor != null
                && !Objects.equals(
                        predecessor.effectiveTo(),
                        predecessorEffectiveTo)) {

            predecessor.setEffectiveTo(predecessorEffectiveTo);
        }

        UserNutritionTarget target = targetRepository.saveAndFlush(
                new UserNutritionTarget(
                        command.userId(),
                        command.effectiveFrom(),
                        newEffectiveTo,
                        command.origin(),
                        command.activityLevelId(),
                        command.nutritionGoalId(),
                        command.calculationMethod(),
                        command.note()));

        if (target == null || target.id() == null) {
            throw new NutritionTargetLifecycleException(
                    NutritionTargetLifecycleFailure.PERSISTENCE_FAILURE,
                    "persisted target id is required");
        }

        List<UserNutritionTargetValue> targetValues =
                new ArrayList<>(command.nutrientValues().size());

        for (NutritionTargetValueDraft value : command.nutrientValues()) {
            targetValues.add(
                    new UserNutritionTargetValue(
                            target,
                            nutrientsByCode.get(value.nutrientCode()),
                            value.targetAmount(),
                            value.minAmount(),
                            value.maxAmount(),
                            value.hardLimit()));
        }

        targetValueRepository.saveAllAndFlush(targetValues);

        return new NutritionTargetLifecycleResult(
                target.id(),
                target.effectiveFrom(),
                target.effectiveTo());
    }

    private Map<String, Nutrient> resolveNutrients(
            List<NutritionTargetValueDraft> valueDrafts) {

        Map<String, Nutrient> nutrientsByCode =
                new LinkedHashMap<>();

        for (NutritionTargetValueDraft value : valueDrafts) {
            Nutrient nutrient = nutrientRepository
                    .findByCode(value.nutrientCode())
                    .orElseThrow(() -> new NutritionTargetLifecycleException(
                            NutritionTargetLifecycleFailure
                                    .UNKNOWN_NUTRIENT_CODE,
                            "unknown nutrient code: "
                                    + value.nutrientCode()));

            nutrientsByCode.put(value.nutrientCode(), nutrient);
        }

        return nutrientsByCode;
    }

    private List<UserNutritionTarget> lockedTimeline(
            Long userId) {

        List<UserNutritionTarget> lockedTargets = targetRepository
                .findAllByUserIdForUpdate(userId);

        if (lockedTargets == null) {
            throw new NutritionTargetLifecycleException(
                    NutritionTargetLifecycleFailure.CORRUPTED_TIMELINE,
                    "target timeline is unavailable");
        }

        List<UserNutritionTarget> orderedTargets =
                new ArrayList<>(lockedTargets);

        for (UserNutritionTarget target : orderedTargets) {
            if (target == null
                    || target.effectiveFrom() == null
                    || !Objects.equals(target.userId(), userId)) {

                throw new NutritionTargetLifecycleException(
                        NutritionTargetLifecycleFailure.CORRUPTED_TIMELINE,
                        "target timeline contains an invalid target");
            }

            if (target.effectiveTo() != null
                    && target.effectiveTo().isBefore(
                            target.effectiveFrom())) {

                throw new NutritionTargetLifecycleException(
                        NutritionTargetLifecycleFailure.CORRUPTED_TIMELINE,
                        "target timeline contains an invalid period");
            }
        }

        orderedTargets.sort(
                Comparator.comparing(UserNutritionTarget::effectiveFrom));

        validateNoOverlap(
                toPeriods(orderedTargets),
                NutritionTargetLifecycleFailure.CORRUPTED_TIMELINE,
                "existing target timeline overlaps");

        return orderedTargets;
    }

    private static void rejectSameEffectiveDate(
            List<UserNutritionTarget> timeline,
            LocalDate effectiveFrom) {

        for (UserNutritionTarget target : timeline) {
            if (target.effectiveFrom().equals(effectiveFrom)) {
                throw new NutritionTargetLifecycleException(
                        NutritionTargetLifecycleFailure.SAME_EFFECTIVE_DATE,
                        "a target already starts on " + effectiveFrom);
            }
        }
    }

    private static LocalDate derivedPredecessorEnd(
            UserNutritionTarget predecessor,
            LocalDate newEffectiveFrom) {

        LocalDate dayBeforeNewTarget = newEffectiveFrom.minusDays(1);

        if (predecessor.effectiveTo() == null
                || predecessor.effectiveTo().isAfter(
                        dayBeforeNewTarget)) {

            return dayBeforeNewTarget;
        }

        return predecessor.effectiveTo();
    }

    private static void verifyResultingTimeline(
            List<UserNutritionTarget> existingTargets,
            UserNutritionTarget predecessor,
            LocalDate predecessorEffectiveTo,
            LocalDate newEffectiveFrom,
            LocalDate newEffectiveTo) {

        List<TimelinePeriod> resultingPeriods =
                new ArrayList<>(existingTargets.size() + 1);

        for (UserNutritionTarget target : existingTargets) {
            LocalDate effectiveTo = target == predecessor
                    ? predecessorEffectiveTo
                    : target.effectiveTo();

            resultingPeriods.add(
                    new TimelinePeriod(
                            target.effectiveFrom(),
                            effectiveTo));
        }

        resultingPeriods.add(
                new TimelinePeriod(
                        newEffectiveFrom,
                        newEffectiveTo));

        validateNoOverlap(
                resultingPeriods,
                NutritionTargetLifecycleFailure.OVERLAPPING_TIMELINE,
                "target insertion would overlap the existing timeline");
    }

    private static List<TimelinePeriod> toPeriods(
            List<UserNutritionTarget> targets) {

        List<TimelinePeriod> periods =
                new ArrayList<>(targets.size());

        for (UserNutritionTarget target : targets) {
            periods.add(
                    new TimelinePeriod(
                            target.effectiveFrom(),
                            target.effectiveTo()));
        }

        return periods;
    }

    private static void validateNoOverlap(
            List<TimelinePeriod> periods,
            NutritionTargetLifecycleFailure failure,
            String message) {

        List<TimelinePeriod> orderedPeriods =
                new ArrayList<>(periods);
        orderedPeriods.sort(
                Comparator.comparing(TimelinePeriod::effectiveFrom));

        TimelinePeriod previous = null;

        for (TimelinePeriod current : orderedPeriods) {
            if (current.effectiveTo() != null
                    && current.effectiveTo().isBefore(
                            current.effectiveFrom())) {

                throw new NutritionTargetLifecycleException(
                        failure,
                        message);
            }

            if (previous != null
                    && (previous.effectiveTo() == null
                        || !previous.effectiveTo().isBefore(
                                current.effectiveFrom()))) {

                throw new NutritionTargetLifecycleException(
                        failure,
                        message);
            }

            previous = current;
        }
    }

    private record TimelinePeriod(
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }
}
