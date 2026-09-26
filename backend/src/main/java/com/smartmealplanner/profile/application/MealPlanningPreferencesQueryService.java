package com.smartmealplanner.profile.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.profile.persistence.UserAllergenRepository;
import com.smartmealplanner.profile.persistence.UserDietaryPreferenceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile-owned authenticated read; every declared allergen is a hard exclusion. */
@Service
public class MealPlanningPreferencesQueryService {
    private final CurrentUserService users;
    private final UserAllergenRepository allergens;
    private final UserDietaryPreferenceRepository preferences;

    public MealPlanningPreferencesQueryService(CurrentUserService users,
            UserAllergenRepository allergens, UserDietaryPreferenceRepository preferences) {
        this.users = users;
        this.allergens = allergens;
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public MealPlanningPreferencesSnapshot forUser(UUID authenticatedPublicId) {
        Long userId = users.getIdentity(authenticatedPublicId).internalId();
        List<String> hard = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        preferences.findByUserIdWithPreference(userId).forEach(value -> {
            if (value.preference().isExclusionary()) {
                hard.add(value.preference().code());
            } else {
                soft.add(value.preference().code());
            }
        });
        return new MealPlanningPreferencesSnapshot(
                allergens.findByUserIdWithAllergen(userId).stream()
                        .map(value -> value.allergen().code()).toList(), hard, soft);
    }
}
