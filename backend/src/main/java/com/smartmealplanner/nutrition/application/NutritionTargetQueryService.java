package com.smartmealplanner.nutrition.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueRepository;
import com.smartmealplanner.profile.application.NutritionProfileQueryService;
import com.smartmealplanner.profile.application.NutritionProfileReferenceCodes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticated current-target and bounded-history application reads.
 */
@Service
public class NutritionTargetQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUserService currentUserService;
    private final NutritionProfileQueryService profileQueryService;
    private final UserNutritionTargetRepository targetRepository;
    private final UserNutritionTargetValueRepository targetValueRepository;
    private final Clock clock;

    @Autowired
    public NutritionTargetQueryService(
            CurrentUserService currentUserService,
            NutritionProfileQueryService profileQueryService,
            UserNutritionTargetRepository targetRepository,
            UserNutritionTargetValueRepository targetValueRepository) {

        this(
                currentUserService,
                profileQueryService,
                targetRepository,
                targetValueRepository,
                Clock.systemUTC());
    }

    public NutritionTargetQueryService(
            CurrentUserService currentUserService,
            NutritionProfileQueryService profileQueryService,
            UserNutritionTargetRepository targetRepository,
            UserNutritionTargetValueRepository targetValueRepository,
            Clock clock) {

        this.currentUserService = currentUserService;
        this.profileQueryService = profileQueryService;
        this.targetRepository = targetRepository;
        this.targetValueRepository = targetValueRepository;
        this.clock = clock;
    }

    /**
     * Returns the target whose period contains the authenticated user's local
     * calendar date.
     */
    @Transactional(readOnly = true)
    public NutritionTargetView getCurrentTarget(
            UUID authenticatedPublicId) {

        return findCurrentTarget(authenticatedPublicId)
                .orElseThrow(
                        () -> new NutritionApplicationException(
                                NutritionApplicationFailure.NO_CURRENT_TARGET,
                                "No current nutrition target is available"));
    }

    /**
     * Reads the current target without throwing for an absent target.
     *
     * <p>This distinction matters to callers that are already inside a write
     * transaction: an expected absence must not be raised as a runtime
     * exception and then caught after Spring has marked the transaction
     * rollback-only.</p>
     */
    @Transactional(readOnly = true)
    public Optional<NutritionTargetView> findCurrentTarget(
            UUID authenticatedPublicId) {

        CurrentUserIdentity identity = currentUserService.getIdentity(
                authenticatedPublicId);
        ZoneId zoneId = parseZone(identity.timeZone());
        LocalDate localDate = LocalDate.ofInstant(
                clock.instant(),
                zoneId);

        List<UserNutritionTarget> matches = targetRepository
                .findByUserIdAndEffectiveOn(
                        identity.internalId(),
                        localDate);

        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }

        if (matches.size() > 1) {
            throw new NutritionApplicationException(
                    NutritionApplicationFailure.CORRUPTED_TARGET_TIMELINE,
                    "Multiple current nutrition targets are present");
        }

        UserNutritionTarget target = matches.get(0);
        if (target == null) {
            throw new NutritionApplicationException(
                    NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                    "Nutrition target data is inconsistent");
        }

        Map<Long, List<UserNutritionTargetValue>> valuesByTargetId =
                valuesByTargetId(List.of(target));
        NutritionProfileReferenceCodes references = referenceCodesFor(
                List.of(target));

        return Optional.of(
                NutritionTargetViewAssembler.toView(
                        target,
                        valuesByTargetId.get(target.id()),
                        references));
    }

    /**
     * Returns newest-first target history using repository-level pagination.
     */
    @Transactional(readOnly = true)
    public NutritionTargetHistoryPage getTargetHistory(
            UUID authenticatedPublicId,
            int page,
            int size) {

        CurrentUserIdentity identity = currentUserService.getIdentity(
                authenticatedPublicId);
        validatePagination(page, size);

        Page<UserNutritionTarget> targetPage = targetRepository
                .findByUserIdOrderByEffectiveFromDescIdDesc(
                        identity.internalId(),
                        PageRequest.of(page, size));

        List<UserNutritionTarget> targets = targetPage.getContent();
        Map<Long, List<UserNutritionTargetValue>> valuesByTargetId =
                valuesByTargetId(targets);
        NutritionProfileReferenceCodes references = referenceCodesFor(
                targets);

        List<NutritionTargetView> content = new ArrayList<>(
                targets.size());
        for (UserNutritionTarget target : targets) {
            content.add(
                    NutritionTargetViewAssembler.toView(
                            target,
                            valuesByTargetId.get(target.id()),
                            references));
        }

        return new NutritionTargetHistoryPage(
                targetPage.getNumber(),
                targetPage.getSize(),
                targetPage.getTotalElements(),
                targetPage.getTotalPages(),
                content);
    }

    private Map<Long, List<UserNutritionTargetValue>> valuesByTargetId(
            Collection<UserNutritionTarget> targets) {

        Map<Long, List<UserNutritionTargetValue>> valuesByTargetId =
                new HashMap<>();
        Set<Long> targetIds = new HashSet<>();

        for (UserNutritionTarget target : targets) {
            if (target == null || target.id() == null) {
                throw new NutritionApplicationException(
                        NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                        "Nutrition target data is inconsistent");
            }

            targetIds.add(target.id());
        }

        if (targetIds.isEmpty()) {
            return valuesByTargetId;
        }

        for (UserNutritionTargetValue value : targetValueRepository
                .findAllByTargetIdInWithNutrientAndUnit(targetIds)) {

            if (value == null
                    || value.id() == null
                    || value.id().targetId() == null
                    || !targetIds.contains(value.id().targetId())) {

                throw new NutritionApplicationException(
                        NutritionApplicationFailure.CORRUPTED_TARGET_DATA,
                        "Nutrition target value data is inconsistent");
            }

            valuesByTargetId
                    .computeIfAbsent(
                            value.id().targetId(),
                            ignored -> new ArrayList<>())
                    .add(value);
        }

        return valuesByTargetId;
    }

    private NutritionProfileReferenceCodes referenceCodesFor(
            Collection<UserNutritionTarget> targets) {

        Set<Long> activityLevelIds = new HashSet<>();
        Set<Long> nutritionGoalIds = new HashSet<>();

        for (UserNutritionTarget target : targets) {
            if (target.activityLevelId() != null) {
                activityLevelIds.add(target.activityLevelId());
            }

            if (target.nutritionGoalId() != null) {
                nutritionGoalIds.add(target.nutritionGoalId());
            }
        }

        return profileQueryService.getNutritionReferenceCodes(
                activityLevelIds,
                nutritionGoalIds);
    }

    private static ZoneId parseZone(
            String timeZone) {

        if (timeZone == null || timeZone.isBlank()) {
            throw new NutritionApplicationException(
                    NutritionApplicationFailure.INVALID_TIME_ZONE,
                    "Configured time zone is invalid");
        }

        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException exception) {
            throw new NutritionApplicationException(
                    NutritionApplicationFailure.INVALID_TIME_ZONE,
                    "Configured time zone is invalid",
                    exception);
        }
    }

    private static void validatePagination(
            int page,
            int size) {

        if (page < 0) {
            throw NutritionApplicationException.invalidRequest(
                    "page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw NutritionApplicationException.invalidRequest(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
