package com.smartmealplanner.profile.web;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.profile.application.UserAllergenService;
import com.smartmealplanner.profile.application.UserDietaryPreferenceService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserPreferencesController {

    private final UserDietaryPreferenceService dietaryPreferenceService;
    private final UserAllergenService allergenService;

    public UserPreferencesController(
            UserDietaryPreferenceService dietaryPreferenceService,
            UserAllergenService allergenService) {

        this.dietaryPreferenceService =
                dietaryPreferenceService;

        this.allergenService =
                allergenService;
    }

    @GetMapping("/dietary-preferences")
    public List<UserDietaryPreferenceResponse> getDietaryPreferences(
            @AuthenticationPrincipal
            Jwt jwt) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return dietaryPreferenceService.getPreferences(
                publicId);
    }

    @PutMapping("/dietary-preferences")
    public List<UserDietaryPreferenceResponse> replaceDietaryPreferences(
            @AuthenticationPrincipal
            Jwt jwt,
            @RequestBody
            ReplaceDietaryPreferencesRequest request) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return dietaryPreferenceService.replacePreferences(
                publicId,
                request);
    }

    @GetMapping("/allergens")
    public List<UserAllergenResponse> getAllergens(
            @AuthenticationPrincipal
            Jwt jwt) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return allergenService.getAllergens(
                publicId);
    }

    @PutMapping("/allergens")
    public List<UserAllergenResponse> replaceAllergens(
            @AuthenticationPrincipal
            Jwt jwt,
            @RequestBody
            ReplaceAllergensRequest request) {

        UUID publicId =
                SecurityPrincipals.authenticatedPublicId(
                        jwt);

        return allergenService.replaceAllergens(
                publicId,
                request);
    }
}
