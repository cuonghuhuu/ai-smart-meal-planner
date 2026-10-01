package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.ScoreComponentCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Entry;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Gap;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.GeneratedPlan;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceCommands.Score;
import com.smartmealplanner.mealplanning.persistence.MealPlanPersistenceException.Reason;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** TX2: writes one validated terminal outcome without invoking any remote service. */
@Service
public class MealPlanTerminalWriter {
    private static final BigDecimal MAX_SERVINGS = new BigDecimal("50");
    private static final BigDecimal MIN_SCORE = new BigDecimal("-0.20");

    private final RecommendationRequestRepository requests;
    private final RecommendationResultRepository results;
    private final RecommendationResultScoreRepository scores;
    private final MealPlanRepository plans;
    private final MealPlanEntryRepository entries;
    private final MealPlanUnfilledSlotRepository gaps;
    private final MealPlanReferenceRepository references;

    public MealPlanTerminalWriter(RecommendationRequestRepository requests,
            RecommendationResultRepository results,
            RecommendationResultScoreRepository scores, MealPlanRepository plans,
            MealPlanEntryRepository entries, MealPlanUnfilledSlotRepository gaps,
            MealPlanReferenceRepository references) {
        this.requests = requests;
        this.results = results;
        this.scores = scores;
        this.plans = plans;
        this.entries = entries;
        this.gaps = gaps;
        this.references = references;
    }

    /** Persists SUCCEEDED or DEGRADED with the complete graph in one transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredPlan persistGenerated(GeneratedPlan command) {
        validateGenerated(command);
        RecommendationRequest request = pendingRequest(command.requestId());
        requireEmptyGraph(command.requestId());
        Map<MealSlotCode, MealPlanReferenceRepository.Slot> slots = references.slots();
        Map<ScoreComponentCode, Long> components = references.scoreComponents();
        for (MealSlotCode code : command.requestedMealSlots()) { slot(slots, code); }

        List<Entry> ordered = new ArrayList<>(command.entries());
        ordered.sort(Comparator.comparing(Entry::planDate)
                .thenComparingInt(entry -> slot(slots, entry.mealSlotCode()).displayOrder())
                .thenComparing(entry -> entry.mealSlotCode().name())
                .thenComparingInt(Entry::positionInSlot));

        List<StoredEntry> stored = new ArrayList<>(ordered.size());
        short rank = 1;
        for (Entry entry : ordered) {
            RecommendationResult result = results.saveAndFlush(new RecommendationResult(
                    request, rank++, entry.recipeId(), null, entry.totalScore(),
                    entry.explanation()));
            for (Score score : entry.scores()) {
                Long componentId = components.get(score.componentCode());
                if (componentId == null) { throw failure(Reason.MISSING_REFERENCE); }
                scores.saveAndFlush(new RecommendationResultScore(result, componentId,
                        score.value(), score.weight()));
            }
            stored.add(new StoredEntry(entry, result));
        }

        MealPlan plan = plans.saveAndFlush(new MealPlan(request.userId(),
                command.startDate(), command.endDate(), command.defaultServings(), request));
        for (StoredEntry storedEntry : stored) {
            Entry entry = storedEntry.entry();
            entries.saveAndFlush(new MealPlanEntry(plan, entry.planDate(),
                    slot(slots, entry.mealSlotCode()).id(), entry.positionInSlot(),
                    entry.recipeId(), null, entry.servings(),
                    MealPlanEntry.Provenance.AI_GENERATED, storedEntry.result()));
        }
        if (command.status() == GenerationStatus.DEGRADED) {
            List<Gap> orderedGaps = new ArrayList<>(command.gaps());
            orderedGaps.sort(Comparator.comparing(Gap::planDate)
                    .thenComparingInt(gap -> slot(slots, gap.mealSlotCode()).displayOrder())
                    .thenComparing(gap -> gap.mealSlotCode().name()));
            for (Gap gap : orderedGaps) {
                gaps.saveAndFlush(new MealPlanUnfilledSlot(plan, gap.planDate(),
                        slot(slots, gap.mealSlotCode()).id(),
                        MealPlanUnfilledSlot.Reason.valueOf(gap.reasonCode().name()),
                        gap.explanation()));
            }
        }
        if (requests.completePending(request.id(), command.status().name(),
                command.durationMs(), null) != 1) {
            throw failure(Reason.INVALID_TRANSITION);
        }
        return new StoredPlan(plan.id(), plan.publicId());
    }

    /** Internal database identity and the stable public plan identity. */
    public record StoredPlan(Long id, UUID publicId) { }

    /** Records an algorithm conclusion without creating a plan or result graph. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInfeasible(Long requestId, Integer durationMs) {
        if (requestId == null || requestId <= 0 || !validDuration(durationMs)) {
            throw failure(Reason.INVALID_COMMAND);
        }
        pendingRequest(requestId);
        requireEmptyGraph(requestId);
        if (requests.completePending(requestId, RecommendationRequest.Status.INFEASIBLE.name(),
                durationMs, null) != 1) {
            throw failure(Reason.INVALID_TRANSITION);
        }
    }

    private RecommendationRequest pendingRequest(Long requestId) {
        RecommendationRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> failure(Reason.REQUEST_NOT_FOUND));
        if (request.requestKind() != RecommendationRequest.Kind.MEAL_PLAN
                || request.status() != RecommendationRequest.Status.PENDING) {
            throw failure(Reason.INVALID_TRANSITION);
        }
        return request;
    }

    private void requireEmptyGraph(Long requestId) {
        if (plans.existsBySourceRequestId(requestId)) { throw failure(Reason.DUPLICATE_PLAN); }
        if (results.existsByRequestId(requestId)) { throw failure(Reason.EXISTING_GRAPH); }
    }

    private static void validateGenerated(GeneratedPlan command) {
        if (command == null || command.requestId() == null || command.requestId() <= 0
                || command.status() != GenerationStatus.SUCCEEDED
                && command.status() != GenerationStatus.DEGRADED
                || command.startDate() == null || command.endDate() == null
                || !validDuration(command.durationMs())
                || !validServings(command.defaultServings())
                || command.requestedMealSlots() == null
                || command.requestedMealSlots().isEmpty()
                || command.requestedMealSlots().size()
                > MealPlanningContractLimits.MAX_REQUESTED_MEAL_SLOTS
                || command.entries() == null || command.gaps() == null) {
            throw failure(Reason.INVALID_COMMAND);
        }
        long days = ChronoUnit.DAYS.between(command.startDate(), command.endDate()) + 1;
        if (days < 1 || days > MealPlanningContractLimits.MAX_PLAN_DAYS
                || command.entries().isEmpty()
                || command.entries().size() + command.gaps().size()
                > MealPlanningContractLimits.MAX_EXPANDED_PLAN_SLOTS
                || command.status() == GenerationStatus.SUCCEEDED
                && !command.gaps().isEmpty()
                || command.status() == GenerationStatus.DEGRADED
                && command.gaps().isEmpty()) {
            throw failure(Reason.INVALID_COMMAND);
        }

        Set<SlotKey> expected = new HashSet<>();
        for (int day = 0; day < days; day++) {
            LocalDate date = command.startDate().plusDays(day);
            for (MealSlotCode slot : command.requestedMealSlots()) {
                if (slot == null || !expected.add(new SlotKey(date, slot))) {
                    throw failure(Reason.INVALID_COMMAND);
                }
            }
        }
        Set<SlotKey> filled = new HashSet<>();
        Set<EntryKey> positions = new HashSet<>();
        for (Entry entry : command.entries()) {
            if (entry == null || entry.planDate() == null || entry.mealSlotCode() == null
                    || entry.positionInSlot() < 1 || entry.recipeId() == null
                    || entry.recipeId() <= 0 || !validServings(entry.servings())
                    || entry.totalScore() == null || entry.totalScore().scale() > 6
                    || entry.totalScore().compareTo(MIN_SCORE) < 0
                    || entry.totalScore().compareTo(BigDecimal.ONE) > 0
                    || entry.explanation() == null || entry.explanation().isBlank()
                    || entry.explanation().length() > 500 || !validScores(entry.scores())) {
                throw failure(Reason.INVALID_COMMAND);
            }
            SlotKey key = new SlotKey(entry.planDate(), entry.mealSlotCode());
            if (!expected.contains(key)
                    || !positions.add(new EntryKey(key, entry.positionInSlot()))) {
                throw failure(Reason.INVALID_COMMAND);
            }
            filled.add(key);
        }
        Set<SlotKey> actual = new HashSet<>(filled);
        for (Gap gap : command.gaps()) {
            if (gap == null || gap.planDate() == null || gap.mealSlotCode() == null
                    || gap.reasonCode() == null
                    || gap.explanation() != null && gap.explanation().length() > 500) {
                throw failure(Reason.INVALID_COMMAND);
            }
            SlotKey key = new SlotKey(gap.planDate(), gap.mealSlotCode());
            if (!expected.contains(key) || !actual.add(key)) {
                throw failure(Reason.INVALID_COMMAND);
            }
        }
        if (!actual.equals(expected)) { throw failure(Reason.INVALID_COMMAND); }
    }

    private static boolean validScores(List<Score> scores) {
        if (scores == null || scores.size() != ScoreComponentCode.values().length) {
            return false;
        }
        Set<ScoreComponentCode> codes = EnumSet.noneOf(ScoreComponentCode.class);
        for (Score score : scores) {
            if (score == null || score.componentCode() == null
                    || !codes.add(score.componentCode()) || score.value() == null
                    || score.value().signum() < 0 || score.value().compareTo(BigDecimal.ONE) > 0
                    || score.value().scale() > 6 || score.weight() == null
                    || score.weight().scale() > 4
                    || score.weight().compareTo(score.componentCode().expectedPositiveWeight()) != 0) {
                return false;
            }
        }
        return codes.size() == ScoreComponentCode.values().length;
    }

    private static boolean validServings(BigDecimal servings) {
        return servings != null && servings.signum() > 0
                && servings.compareTo(MAX_SERVINGS) <= 0 && servings.scale() <= 2;
    }

    private static boolean validDuration(Integer durationMs) {
        return durationMs == null || durationMs >= 0;
    }

    private static MealPlanReferenceRepository.Slot slot(
            Map<MealSlotCode, MealPlanReferenceRepository.Slot> slots, MealSlotCode code) {
        MealPlanReferenceRepository.Slot slot = slots.get(code);
        if (slot == null) { throw failure(Reason.MISSING_REFERENCE); }
        return slot;
    }

    private static MealPlanPersistenceException failure(Reason reason) {
        return new MealPlanPersistenceException(reason);
    }

    private record SlotKey(LocalDate date, MealSlotCode slot) { }
    private record EntryKey(SlotKey slot, short position) { }
    private record StoredEntry(Entry entry, RecommendationResult result) { }
}
