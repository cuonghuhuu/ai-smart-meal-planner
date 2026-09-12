package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.auth.application.AuthenticationFailedException;
import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;
import com.smartmealplanner.profile.persistence.Sex;
import com.smartmealplanner.profile.persistence.UserProfile;
import com.smartmealplanner.profile.persistence.UserProfileRepository;
import com.smartmealplanner.profile.web.ProfileResponse;
import com.smartmealplanner.profile.web.UpdateProfileRequest;
import com.smartmealplanner.shared.web.InvalidRequestException;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private ActivityLevelRepository activityLevelRepository;

    @Mock
    private NutritionGoalRepository nutritionGoalRepository;

    @Mock
    private CurrentUserService currentUserService;

    private Clock clock;
    private UserProfileService service;

    private final UUID publicId = UUID.randomUUID();
    private final Long internalId = 42L;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-12T12:00:00Z"),
                ZoneOffset.UTC);

        service = new UserProfileService(
                profileRepository,
                activityLevelRepository,
                nutritionGoalRepository,
                currentUserService,
                clock);
    }

    @Test
    void getProfileReturnsProfileWhenPresent() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        UserProfile profile = new UserProfile(internalId);
        profile.update(
                LocalDate.of(1995, 5, 20),
                Sex.FEMALE,
                BigDecimal.valueOf(165.5),
                null,
                null,
                BigDecimal.valueOf(55.0),
                BigDecimal.valueOf(-0.5),
                2,
                45,
                "Loves veggies");

        when(profileRepository.findByUserIdWithReferences(internalId))
                .thenReturn(Optional.of(profile));

        ProfileResponse response = service.getProfile(publicId);

        assertThat(response).isNotNull();
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1995, 5, 20));
        assertThat(response.sex()).isEqualTo(Sex.FEMALE);
        assertThat(response.heightCm()).isEqualTo(BigDecimal.valueOf(165.5));
        assertThat(response.householdSize()).isEqualTo(2);
        assertThat(response.maxCookMinutes()).isEqualTo(45);
        assertThat(response.notes()).isEqualTo("Loves veggies");
    }

    @Test
    void getProfileThrowsEntityNotFoundWhenProfileAbsent() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        when(profileRepository.findByUserIdWithReferences(internalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(publicId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User profile not found");
    }

    @Test
    void getProfilePropagatesAuthenticationExceptionWhenIdentityFails() {
        when(currentUserService.getIdentity(publicId))
                .thenThrow(new AuthenticationFailedException());

        assertThatThrownBy(() -> service.getProfile(publicId))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void updateProfileCreatesProfileWhenNoneExists() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        when(profileRepository.findById(internalId))
                .thenReturn(Optional.empty());

        ActivityLevel activity = new ActivityLevel("MODERATE", "Moderate", "Desc", BigDecimal.valueOf(1.55), 30);
        when(activityLevelRepository.findByCode("MODERATE"))
                .thenReturn(Optional.of(activity));

        NutritionGoal goal = new NutritionGoal("MAINTAIN", "Maintain", "Desc", 20);
        when(nutritionGoalRepository.findByCode("MAINTAIN"))
                .thenReturn(Optional.of(goal));

        when(profileRepository.saveAndFlush(any(UserProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
                LocalDate.of(1990, 1, 15),
                Sex.MALE,
                BigDecimal.valueOf(180.0),
                "MODERATE",
                "MAINTAIN",
                BigDecimal.valueOf(75.0),
                BigDecimal.ZERO,
                1,
                30,
                "No seafood",
                null);

        ProfileResponse response = service.updateProfile(publicId, request);

        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(response.sex()).isEqualTo(Sex.MALE);
        assertThat(response.activityLevel()).isEqualTo("MODERATE");
        assertThat(response.nutritionGoal()).isEqualTo("MAINTAIN");
        verify(profileRepository).saveAndFlush(any(UserProfile.class));
    }

    @Test
    void updateProfileRejectsVersionMismatchOnExistingProfile() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        UserProfile existing = new UserProfile(internalId);
        // emulate loaded version 2
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "version", 2L);

        when(profileRepository.findById(internalId))
                .thenReturn(Optional.of(existing));

        UpdateProfileRequest request = new UpdateProfileRequest(
                LocalDate.of(1990, 1, 15),
                Sex.MALE,
                BigDecimal.valueOf(180.0),
                null,
                null,
                null,
                null,
                1,
                null,
                null,
                1L); // version 1 != 2

        assertThatThrownBy(() -> service.updateProfile(publicId, request))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void updateProfileRejectsNonZeroVersionOnNewProfile() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        when(profileRepository.findById(internalId))
                .thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest(
                LocalDate.of(1990, 1, 15),
                Sex.MALE,
                BigDecimal.valueOf(180.0),
                null,
                null,
                null,
                null,
                1,
                null,
                null,
                3L);

        assertThatThrownBy(() -> service.updateProfile(publicId, request))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void updateProfileValidatesStructuralConstraints() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));
        when(profileRepository.findById(internalId))
                .thenReturn(Optional.empty());

        // Birth date in future
        UpdateProfileRequest futureBirth = new UpdateProfileRequest(
                LocalDate.of(2030, 1, 1), null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, futureBirth))
                .isInstanceOf(InvalidRequestException.class);

        // Birth date before 1900
        UpdateProfileRequest ancientBirth = new UpdateProfileRequest(
                LocalDate.of(1899, 12, 31), null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, ancientBirth))
                .isInstanceOf(InvalidRequestException.class);

        // Height <= 30
        UpdateProfileRequest tooShort = new UpdateProfileRequest(
                null, null, BigDecimal.valueOf(30), null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, tooShort))
                .isInstanceOf(InvalidRequestException.class);

        // Height >= 300
        UpdateProfileRequest tooTall = new UpdateProfileRequest(
                null, null, BigDecimal.valueOf(300), null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, tooTall))
                .isInstanceOf(InvalidRequestException.class);

        // Target weight <= 2
        UpdateProfileRequest tooLight = new UpdateProfileRequest(
                null, null, null, null, null, BigDecimal.valueOf(2), null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, tooLight))
                .isInstanceOf(InvalidRequestException.class);

        // Target weight >= 700
        UpdateProfileRequest tooHeavy = new UpdateProfileRequest(
                null, null, null, null, null, BigDecimal.valueOf(700), null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, tooHeavy))
                .isInstanceOf(InvalidRequestException.class);

        // Weekly change <= -5
        UpdateProfileRequest tooFastLoss = new UpdateProfileRequest(
                null, null, null, null, null, null, BigDecimal.valueOf(-5), null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, tooFastLoss))
                .isInstanceOf(InvalidRequestException.class);

        // Weekly change >= 5
        UpdateProfileRequest tooFastGain = new UpdateProfileRequest(
                null, null, null, null, null, null, BigDecimal.valueOf(5), null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, tooFastGain))
                .isInstanceOf(InvalidRequestException.class);

        // Household size < 1
        UpdateProfileRequest invalidHousehold = new UpdateProfileRequest(
                null, null, null, null, null, null, null, 0, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, invalidHousehold))
                .isInstanceOf(InvalidRequestException.class);

        // Max cook minutes < 1
        UpdateProfileRequest cookZero = new UpdateProfileRequest(
                null, null, null, null, null, null, null, null, 0, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, cookZero))
                .isInstanceOf(InvalidRequestException.class);

        // Max cook minutes > 1440
        UpdateProfileRequest cookTooLong = new UpdateProfileRequest(
                null, null, null, null, null, null, null, null, 1441, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, cookTooLong))
                .isInstanceOf(InvalidRequestException.class);

        // Notes > 500
        UpdateProfileRequest longNotes = new UpdateProfileRequest(
                null, null, null, null, null, null, null, null, null, "a".repeat(501), null);
        assertThatThrownBy(() -> service.updateProfile(publicId, longNotes))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void updateProfileRejectsUnknownActivityLevelAndNutritionGoal() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));
        when(profileRepository.findById(internalId))
                .thenReturn(Optional.empty());

        when(activityLevelRepository.findByCode("NON_EXISTENT"))
                .thenReturn(Optional.empty());

        UpdateProfileRequest badActivity = new UpdateProfileRequest(
                null, null, null, "NON_EXISTENT", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, badActivity))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown activity level");

        when(activityLevelRepository.findByCode("SEDENTARY"))
                .thenReturn(Optional.of(new ActivityLevel("SEDENTARY", "Sedentary", null, BigDecimal.valueOf(1.2), 10)));
        when(nutritionGoalRepository.findByCode("NON_EXISTENT_GOAL"))
                .thenReturn(Optional.empty());

        UpdateProfileRequest badGoal = new UpdateProfileRequest(
                null, null, null, "SEDENTARY", "NON_EXISTENT_GOAL", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateProfile(publicId, badGoal))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown nutrition goal");
    }
}
