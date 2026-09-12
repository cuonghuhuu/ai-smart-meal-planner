package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;
import com.smartmealplanner.profile.persistence.UserProfile;
import com.smartmealplanner.profile.persistence.UserProfileRepository;
import com.smartmealplanner.profile.web.ProfileResponse;
import com.smartmealplanner.profile.web.UpdateProfileRequest;
import com.smartmealplanner.shared.web.InvalidRequestException;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private static final LocalDate MIN_BIRTH_DATE =
            LocalDate.of(1900, 1, 1);

    private static final BigDecimal MIN_HEIGHT_CM =
            BigDecimal.valueOf(30);

    private static final BigDecimal MAX_HEIGHT_CM =
            BigDecimal.valueOf(300);

    private static final BigDecimal MIN_TARGET_WEIGHT_KG =
            BigDecimal.valueOf(2);

    private static final BigDecimal MAX_TARGET_WEIGHT_KG =
            BigDecimal.valueOf(700);

    private static final BigDecimal MIN_WEEKLY_CHANGE_KG =
            BigDecimal.valueOf(-5);

    private static final BigDecimal MAX_WEEKLY_CHANGE_KG =
            BigDecimal.valueOf(5);

    private static final int MAX_NOTES_LENGTH =
            500;

    private static final int MIN_COOK_MINUTES =
            1;

    private static final int MAX_COOK_MINUTES =
            1440;

    private final UserProfileRepository profileRepository;
    private final ActivityLevelRepository activityLevelRepository;
    private final NutritionGoalRepository nutritionGoalRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Autowired
    public UserProfileService(
            UserProfileRepository profileRepository,
            ActivityLevelRepository activityLevelRepository,
            NutritionGoalRepository nutritionGoalRepository,
            CurrentUserService currentUserService) {

        this(
                profileRepository,
                activityLevelRepository,
                nutritionGoalRepository,
                currentUserService,
                Clock.systemUTC());
    }

    public UserProfileService(
            UserProfileRepository profileRepository,
            ActivityLevelRepository activityLevelRepository,
            NutritionGoalRepository nutritionGoalRepository,
            CurrentUserService currentUserService,
            Clock clock) {

        this.profileRepository =
                profileRepository;

        this.activityLevelRepository =
                activityLevelRepository;

        this.nutritionGoalRepository =
                nutritionGoalRepository;

        this.currentUserService =
                currentUserService;

        this.clock =
                clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(
            UUID publicId) {

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        UserProfile profile =
                profileRepository.findByUserIdWithReferences(
                                identity.internalId())
                        .orElseThrow(
                                () -> new EntityNotFoundException(
                                        "User profile not found"));

        return toResponse(
                profile);
    }

    @Transactional
    public ProfileResponse updateProfile(
            UUID publicId,
            UpdateProfileRequest request) {

        if (request == null) {
            throw new InvalidRequestException(
                    "Profile request is required");
        }

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        UserProfile profile =
                profileRepository.findById(
                                identity.internalId())
                        .orElse(null);

        if (profile != null) {

            if (request.version() != null
                    && !request.version().equals(profile.version())) {

                throw new OptimisticLockingFailureException(
                        "Profile version conflict");
            }

        } else {

            if (request.version() != null
                    && request.version() != 0L) {

                throw new OptimisticLockingFailureException(
                        "Profile version conflict");
            }

            profile = new UserProfile(
                    identity.internalId());
        }

        validateProfileRequest(
                request);

        ActivityLevel activityLevel =
                resolveActivityLevel(
                        request.activityLevel());

        NutritionGoal nutritionGoal =
                resolveNutritionGoal(
                        request.nutritionGoal());

        byte householdSize =
                (byte) (request.householdSize() == null
                        ? 1
                        : request.householdSize());

        Short maxCookMinutes =
                request.maxCookMinutes() == null
                        ? null
                        : request.maxCookMinutes().shortValue();

        profile.update(
                request.birthDate(),
                request.sex(),
                request.heightCm(),
                activityLevel,
                nutritionGoal,
                request.targetWeightKg(),
                request.weeklyChangeKg(),
                householdSize,
                maxCookMinutes,
                request.notes() == null
                        ? null
                        : request.notes().trim());

        UserProfile saved =
                profileRepository.saveAndFlush(
                        profile);

        return toResponse(
                saved);
    }

    private void validateProfileRequest(
            UpdateProfileRequest request) {

        if (request.birthDate() != null) {

            if (!request.birthDate().isAfter(MIN_BIRTH_DATE)) {
                throw new InvalidRequestException(
                        "Birth date must be after 1900-01-01");
            }

            LocalDate today =
                    LocalDate.now(clock);

            if (request.birthDate().isAfter(today)) {
                throw new InvalidRequestException(
                        "Birth date cannot be in the future");
            }
        }

        if (request.heightCm() != null) {

            if (request.heightCm().compareTo(MIN_HEIGHT_CM) <= 0
                    || request.heightCm().compareTo(MAX_HEIGHT_CM) >= 0) {

                throw new InvalidRequestException(
                        "Height must be between 30 and 300 cm");
            }
        }

        if (request.targetWeightKg() != null) {

            if (request.targetWeightKg().compareTo(MIN_TARGET_WEIGHT_KG) <= 0
                    || request.targetWeightKg().compareTo(MAX_TARGET_WEIGHT_KG) >= 0) {

                throw new InvalidRequestException(
                        "Target weight must be between 2 and 700 kg");
            }
        }

        if (request.weeklyChangeKg() != null) {

            if (request.weeklyChangeKg().compareTo(MIN_WEEKLY_CHANGE_KG) <= 0
                    || request.weeklyChangeKg().compareTo(MAX_WEEKLY_CHANGE_KG) >= 0) {

                throw new InvalidRequestException(
                        "Weekly change must be between -5 and 5 kg");
            }
        }

        if (request.householdSize() != null
                && request.householdSize() < 1) {

            throw new InvalidRequestException(
                    "Household size must be at least 1");
        }

        if (request.maxCookMinutes() != null) {

            if (request.maxCookMinutes() < MIN_COOK_MINUTES
                    || request.maxCookMinutes() > MAX_COOK_MINUTES) {

                throw new InvalidRequestException(
                        "Max cook minutes must be between 1 and 1440");
            }
        }

        if (request.notes() != null
                && request.notes().length() > MAX_NOTES_LENGTH) {

            throw new InvalidRequestException(
                    "Notes must not exceed 500 characters");
        }
    }

    private ActivityLevel resolveActivityLevel(
            String code) {

        if (code == null
                || code.isBlank()) {

            return null;
        }

        return activityLevelRepository.findByCode(
                        code.trim())
                .orElseThrow(
                        () -> new InvalidRequestException(
                                "Unknown activity level: " + code));
    }

    private NutritionGoal resolveNutritionGoal(
            String code) {

        if (code == null
                || code.isBlank()) {

            return null;
        }

        return nutritionGoalRepository.findByCode(
                        code.trim())
                .orElseThrow(
                        () -> new InvalidRequestException(
                                "Unknown nutrition goal: " + code));
    }

    private static ProfileResponse toResponse(
            UserProfile profile) {

        return new ProfileResponse(
                profile.birthDate(),
                profile.sex(),
                profile.heightCm(),
                profile.activityLevel() == null
                        ? null
                        : profile.activityLevel().code(),
                profile.nutritionGoal() == null
                        ? null
                        : profile.nutritionGoal().code(),
                profile.targetWeightKg(),
                profile.weeklyChangeKg(),
                profile.householdSize() == null
                        ? 1
                        : profile.householdSize().intValue(),
                profile.maxCookMinutes() == null
                        ? null
                        : profile.maxCookMinutes().intValue(),
                profile.notes(),
                profile.version() == null
                        ? 0L
                        : profile.version(),
                profile.createdAt(),
                profile.updatedAt());
    }
}
