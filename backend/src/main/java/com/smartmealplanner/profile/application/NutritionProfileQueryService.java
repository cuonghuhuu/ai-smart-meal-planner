package com.smartmealplanner.profile.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;
import com.smartmealplanner.profile.persistence.UserBodyMeasurement;
import com.smartmealplanner.profile.persistence.UserBodyMeasurementRepository;
import com.smartmealplanner.profile.persistence.UserProfile;
import com.smartmealplanner.profile.persistence.UserProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile-owned read boundary for Nutrition application workflows.
 *
 * <p>No Profile entity or web DTO crosses this boundary. Historical measurement
 * selection is deliberately performed by the Profile repository.</p>
 */
@Service
public class NutritionProfileQueryService {

    private final UserProfileRepository profileRepository;
    private final UserBodyMeasurementRepository measurementRepository;
    private final ActivityLevelRepository activityLevelRepository;
    private final NutritionGoalRepository nutritionGoalRepository;

    public NutritionProfileQueryService(
            UserProfileRepository profileRepository,
            UserBodyMeasurementRepository measurementRepository,
            ActivityLevelRepository activityLevelRepository,
            NutritionGoalRepository nutritionGoalRepository) {

        this.profileRepository = profileRepository;
        this.measurementRepository = measurementRepository;
        this.activityLevelRepository = activityLevelRepository;
        this.nutritionGoalRepository = nutritionGoalRepository;
    }

    /**
     * Returns the profile snapshot and the latest eligible measurement for the
     * requested historical effective date.
     */
    @Transactional(readOnly = true)
    public Optional<NutritionProfileSnapshot> findNutritionSnapshot(
            Long userId,
            LocalDate effectiveFrom) {

        require(
                userId,
                "userId");
        require(
                effectiveFrom,
                "effectiveFrom");

        Optional<UserProfile> profile = profileRepository
                .findByUserIdWithReferences(userId);

        if (profile.isEmpty()) {
            return Optional.empty();
        }

        UserProfile profileEntity = profile.orElseThrow();
        UserBodyMeasurement measurement = measurementRepository
                .findFirstByUserIdAndMeasuredOnLessThanEqualOrderByMeasuredOnDescIdDesc(
                        userId,
                        effectiveFrom)
                .orElse(null);

        return Optional.of(
                new NutritionProfileSnapshot(
                        profileEntity.birthDate(),
                        profileEntity.sex() == null
                                ? null
                                : profileEntity.sex().name(),
                        profileEntity.heightCm(),
                        profileEntity.activityLevel() == null
                                ? null
                                : profileEntity.activityLevel().id(),
                        profileEntity.activityLevel() == null
                                ? null
                                : profileEntity.activityLevel().code(),
                        profileEntity.activityLevel() == null
                                ? null
                                : profileEntity.activityLevel().energyFactor(),
                        profileEntity.nutritionGoal() == null
                                ? null
                                : profileEntity.nutritionGoal().id(),
                        profileEntity.nutritionGoal() == null
                                ? null
                                : profileEntity.nutritionGoal().code(),
                        measurement == null
                                ? null
                                : measurement.weightKg(),
                        measurement == null
                                ? null
                                : measurement.measuredOn()));
    }

    /**
     * Resolves scalar target-header reference identifiers in one Profile-owned
     * read operation. Missing identifiers remain absent for the caller to
     * classify as corruption rather than being silently converted to null.
     */
    @Transactional(readOnly = true)
    public NutritionProfileReferenceCodes getNutritionReferenceCodes(
            Collection<Long> activityLevelIds,
            Collection<Long> nutritionGoalIds) {

        Set<Long> activityIds = requireIds(
                activityLevelIds,
                "activityLevelIds");
        Set<Long> goalIds = requireIds(
                nutritionGoalIds,
                "nutritionGoalIds");

        Map<Long, String> activityCodes = new HashMap<>();
        for (ActivityLevel activityLevel : activityLevelRepository
                .findAllById(activityIds)) {

            if (activityLevel.id() != null
                    && activityLevel.code() != null) {

                activityCodes.put(
                        activityLevel.id(),
                        activityLevel.code());
            }
        }

        Map<Long, String> goalCodes = new HashMap<>();
        for (NutritionGoal nutritionGoal : nutritionGoalRepository
                .findAllById(goalIds)) {

            if (nutritionGoal.id() != null
                    && nutritionGoal.code() != null) {

                goalCodes.put(
                        nutritionGoal.id(),
                        nutritionGoal.code());
            }
        }

        return new NutritionProfileReferenceCodes(
                activityCodes,
                goalCodes);
    }

    private static Set<Long> requireIds(
            Collection<Long> ids,
            String fieldName) {

        if (ids == null) {
            throw new IllegalArgumentException(
                    fieldName + " are required");
        }

        Set<Long> copy = new HashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw new IllegalArgumentException(
                        fieldName + " must not contain null");
            }

            copy.add(id);
        }

        return copy;
    }

    private static <T> void require(
            T value,
            String fieldName) {

        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }
    }
}
