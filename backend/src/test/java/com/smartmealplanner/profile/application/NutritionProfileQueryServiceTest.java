package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.MeasurementSource;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;
import com.smartmealplanner.profile.persistence.Sex;
import com.smartmealplanner.profile.persistence.UserBodyMeasurement;
import com.smartmealplanner.profile.persistence.UserBodyMeasurementRepository;
import com.smartmealplanner.profile.persistence.UserProfile;
import com.smartmealplanner.profile.persistence.UserProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionProfileQueryServiceTest {

    private static final Long USER_ID = 42L;

    private static final LocalDate EFFECTIVE_FROM =
            LocalDate.of(2026, 9, 15);

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private UserBodyMeasurementRepository measurementRepository;

    @Mock
    private ActivityLevelRepository activityLevelRepository;

    @Mock
    private NutritionGoalRepository nutritionGoalRepository;

    @Mock
    private UserProfile profile;

    @Mock
    private ActivityLevel activityLevel;

    @Mock
    private NutritionGoal nutritionGoal;

    private NutritionProfileQueryService service;

    @BeforeEach
    void setUp() {
        service = new NutritionProfileQueryService(
                profileRepository,
                measurementRepository,
                activityLevelRepository,
                nutritionGoalRepository);
    }

    @Test
    void snapshotMapsOnlyPersistenceNeutralCalculationContext() {
        UserBodyMeasurement measurement = new UserBodyMeasurement(
                USER_ID,
                EFFECTIVE_FROM.minusDays(1),
                new BigDecimal("80.25"),
                null,
                null,
                MeasurementSource.USER_ENTERED,
                null);
        stubCompleteProfile();

        when(profileRepository.findByUserIdWithReferences(USER_ID))
                .thenReturn(Optional.of(profile));
        when(measurementRepository
                .findFirstByUserIdAndMeasuredOnLessThanEqualOrderByMeasuredOnDescIdDesc(
                        USER_ID,
                        EFFECTIVE_FROM))
                .thenReturn(Optional.of(measurement));

        Optional<NutritionProfileSnapshot> result = service
                .findNutritionSnapshot(USER_ID, EFFECTIVE_FROM);

        assertThat(result).isPresent();
        NutritionProfileSnapshot snapshot = result.orElseThrow();
        assertThat(snapshot.birthDate())
                .isEqualTo(LocalDate.of(1996, 9, 15));
        assertThat(snapshot.sexCode()).isEqualTo("FEMALE");
        assertThat(snapshot.heightCm())
                .isEqualByComparingTo("165.50");
        assertThat(snapshot.activityLevelId()).isEqualTo(7L);
        assertThat(snapshot.activityLevelCode()).isEqualTo("MODERATE");
        assertThat(snapshot.activityFactor())
                .isEqualByComparingTo("1.550");
        assertThat(snapshot.nutritionGoalId()).isEqualTo(9L);
        assertThat(snapshot.nutritionGoalCode()).isEqualTo("MAINTAIN");
        assertThat(snapshot.selectedWeightKg())
                .isEqualByComparingTo("80.25");
        assertThat(snapshot.selectedWeightMeasuredOn())
                .isEqualTo(EFFECTIVE_FROM.minusDays(1));
        assertThat(snapshot).isNotInstanceOf(UserProfile.class);

        verify(measurementRepository)
                .findFirstByUserIdAndMeasuredOnLessThanEqualOrderByMeasuredOnDescIdDesc(
                        eq(USER_ID),
                        eq(EFFECTIVE_FROM));
    }

    @Test
    void missingMeasurementRemainsMissingInsteadOfUsingAProfileDefault() {
        stubCompleteProfile();
        when(profileRepository.findByUserIdWithReferences(USER_ID))
                .thenReturn(Optional.of(profile));
        when(measurementRepository
                .findFirstByUserIdAndMeasuredOnLessThanEqualOrderByMeasuredOnDescIdDesc(
                        USER_ID,
                        EFFECTIVE_FROM))
                .thenReturn(Optional.empty());

        NutritionProfileSnapshot snapshot = service
                .findNutritionSnapshot(USER_ID, EFFECTIVE_FROM)
                .orElseThrow();

        assertThat(snapshot.selectedWeightKg()).isNull();
        assertThat(snapshot.selectedWeightMeasuredOn()).isNull();
    }

    @Test
    void absentProfileIsReportedWithoutQueryingMeasurements() {
        when(profileRepository.findByUserIdWithReferences(USER_ID))
                .thenReturn(Optional.empty());

        assertThat(service.findNutritionSnapshot(USER_ID, EFFECTIVE_FROM))
                .isEmpty();

        verifyNoInteractions(measurementRepository);
    }

    @Test
    void referenceCodesAreResolvedInBulkWithoutReturningProfileEntities() {
        when(activityLevel.id()).thenReturn(7L);
        when(activityLevel.code()).thenReturn("MODERATE");
        when(nutritionGoal.id()).thenReturn(9L);
        when(nutritionGoal.code()).thenReturn("MAINTAIN");
        when(activityLevelRepository.findAllById(Set.of(7L)))
                .thenReturn(List.of(activityLevel));
        when(nutritionGoalRepository.findAllById(Set.of(9L)))
                .thenReturn(List.of(nutritionGoal));

        NutritionProfileReferenceCodes result = service
                .getNutritionReferenceCodes(Set.of(7L), Set.of(9L));

        assertThat(result.activityLevelCodes())
                .containsEntry(7L, "MODERATE");
        assertThat(result.nutritionGoalCodes())
                .containsEntry(9L, "MAINTAIN");
        assertThat(result.activityLevelCodes().get(7L))
                .isNotEqualTo(activityLevel);
    }

    private void stubCompleteProfile() {
        when(profile.birthDate()).thenReturn(LocalDate.of(1996, 9, 15));
        when(profile.sex()).thenReturn(Sex.FEMALE);
        when(profile.heightCm()).thenReturn(new BigDecimal("165.50"));
        when(profile.activityLevel()).thenReturn(activityLevel);
        when(profile.nutritionGoal()).thenReturn(nutritionGoal);

        when(activityLevel.id()).thenReturn(7L);
        when(activityLevel.code()).thenReturn("MODERATE");
        when(activityLevel.energyFactor()).thenReturn(
                new BigDecimal("1.550"));
        when(nutritionGoal.id()).thenReturn(9L);
        when(nutritionGoal.code()).thenReturn("MAINTAIN");
    }
}
