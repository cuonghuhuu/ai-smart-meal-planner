package com.smartmealplanner.profile.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.profile.persistence.DietaryPreference;
import com.smartmealplanner.profile.persistence.DietaryPreferenceRepository;
import com.smartmealplanner.profile.persistence.UserDietaryPreference;
import com.smartmealplanner.profile.persistence.UserDietaryPreferenceRepository;
import com.smartmealplanner.profile.web.ReplaceDietaryPreferencesRequest;
import com.smartmealplanner.profile.web.UserDietaryPreferenceResponse;
import com.smartmealplanner.shared.web.InvalidRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDietaryPreferenceServiceTest {

    @Mock
    private UserDietaryPreferenceRepository userPreferenceRepository;

    @Mock
    private DietaryPreferenceRepository referenceRepository;

    @Mock
    private CurrentUserService currentUserService;

    private UserDietaryPreferenceService service;

    private final UUID publicId = UUID.randomUUID();
    private final Long internalId = 100L;

    @BeforeEach
    void setUp() {
        service = new UserDietaryPreferenceService(
                userPreferenceRepository,
                referenceRepository,
                currentUserService);
    }

    @Test
    void getPreferencesReturnsMappedResponses() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        DietaryPreference pref = new DietaryPreference(
                "VEGETARIAN", "Vegetarian", "No meat", true, (short) 10);
        UserDietaryPreference udp = new UserDietaryPreference(internalId, pref);

        when(userPreferenceRepository.findByUserIdWithPreference(internalId))
                .thenReturn(List.of(udp));

        List<UserDietaryPreferenceResponse> result = service.getPreferences(publicId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("VEGETARIAN");
        assertThat(result.get(0).displayName()).isEqualTo("Vegetarian");
        assertThat(result.get(0).isExclusionary()).isTrue();
    }

    @Test
    void replacePreferencesReplacesAndDeduplicatesCodes() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        DietaryPreference p1 = new DietaryPreference("VEGETARIAN", "Vegetarian", null, true, (short) 10);
        DietaryPreference p2 = new DietaryPreference("LOW_CARB", "Low Carb", null, false, (short) 80);

        when(referenceRepository.findByCode("VEGETARIAN")).thenReturn(Optional.of(p1));
        when(referenceRepository.findByCode("LOW_CARB")).thenReturn(Optional.of(p2));

        ReplaceDietaryPreferencesRequest request = new ReplaceDietaryPreferencesRequest(
                List.of("VEGETARIAN", "LOW_CARB", "VEGETARIAN")); // duplicate VEGETARIAN

        List<UserDietaryPreferenceResponse> responses = service.replacePreferences(publicId, request);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).code()).isEqualTo("VEGETARIAN");
        assertThat(responses.get(1).code()).isEqualTo("LOW_CARB");

        verify(userPreferenceRepository).deleteByUserId(internalId);
        verify(userPreferenceRepository).flush();
        verify(userPreferenceRepository).saveAllAndFlush(any());
    }

    @Test
    void replacePreferencesRejectsUnknownCode() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        when(referenceRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        ReplaceDietaryPreferencesRequest request = new ReplaceDietaryPreferencesRequest(
                List.of("UNKNOWN"));

        assertThatThrownBy(() -> service.replacePreferences(publicId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unknown dietary preference");
    }

    @Test
    void replacePreferencesRejectsBlankOrNull() {
        // null request
        assertThatThrownBy(() -> service.replacePreferences(publicId, null))
                .isInstanceOf(InvalidRequestException.class);

        // blank code in request
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        ReplaceDietaryPreferencesRequest blankCode = new ReplaceDietaryPreferencesRequest(
                List.of("   "));

        assertThatThrownBy(() -> service.replacePreferences(publicId, blankCode))
                .isInstanceOf(InvalidRequestException.class);
    }
}
