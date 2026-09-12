package com.smartmealplanner.profile.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDietaryPreferenceService {

    private final UserDietaryPreferenceRepository userPreferenceRepository;
    private final DietaryPreferenceRepository referenceRepository;
    private final CurrentUserService currentUserService;

    public UserDietaryPreferenceService(
            UserDietaryPreferenceRepository userPreferenceRepository,
            DietaryPreferenceRepository referenceRepository,
            CurrentUserService currentUserService) {

        this.userPreferenceRepository =
                userPreferenceRepository;

        this.referenceRepository =
                referenceRepository;

        this.currentUserService =
                currentUserService;
    }

    @Transactional(readOnly = true)
    public List<UserDietaryPreferenceResponse> getPreferences(
            UUID publicId) {

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        return userPreferenceRepository.findByUserIdWithPreference(
                        identity.internalId())
                .stream()
                .map(udp -> toResponse(udp.preference()))
                .toList();
    }

    @Transactional
    public List<UserDietaryPreferenceResponse> replacePreferences(
            UUID publicId,
            ReplaceDietaryPreferencesRequest request) {

        if (request == null
                || request.preferences() == null) {

            throw new InvalidRequestException(
                    "Preferences list is required");
        }

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        Set<String> uniqueCodes =
                new LinkedHashSet<>();

        for (String rawCode : request.preferences()) {

            if (rawCode == null
                    || rawCode.isBlank()) {

                throw new InvalidRequestException(
                        "Dietary preference code must not be blank");
            }

            uniqueCodes.add(
                    rawCode.trim());
        }

        List<DietaryPreference> resolvedPreferences =
                new ArrayList<>();

        for (String code : uniqueCodes) {

            DietaryPreference ref =
                    referenceRepository.findByCode(
                                    code)
                            .orElseThrow(
                                    () -> new InvalidRequestException(
                                            "Unknown dietary preference: " + code));

            resolvedPreferences.add(
                    ref);
        }

        userPreferenceRepository.deleteByUserId(
                identity.internalId());

        userPreferenceRepository.flush();

        List<UserDietaryPreference> entitiesToSave =
                resolvedPreferences.stream()
                        .map(ref -> new UserDietaryPreference(identity.internalId(), ref))
                        .toList();

        userPreferenceRepository.saveAllAndFlush(
                entitiesToSave);

        return resolvedPreferences.stream()
                .map(UserDietaryPreferenceService::toResponse)
                .toList();
    }

    private static UserDietaryPreferenceResponse toResponse(
            DietaryPreference preference) {

        return new UserDietaryPreferenceResponse(
                preference.code(),
                preference.displayName(),
                preference.description(),
                preference.isExclusionary());
    }
}
