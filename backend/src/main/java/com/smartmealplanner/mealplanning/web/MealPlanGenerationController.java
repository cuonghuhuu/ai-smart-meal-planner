package com.smartmealplanner.mealplanning.web;

import com.smartmealplanner.mealplanning.orchestration.PersistedMealPlanGenerationService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP adapter for persisted generation. */
@RestController
@RequestMapping("/api/v1/me/meal-plans")
public class MealPlanGenerationController {
    private final PersistedMealPlanGenerationService generation;

    public MealPlanGenerationController(PersistedMealPlanGenerationService generation) {
        this.generation = generation;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public MealPlanGenerationResponse generate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MealPlanGenerationRequest request) {
        return MealPlanGenerationResponse.from(generation.generate(
                SecurityPrincipals.authenticatedPublicId(jwt), request.toCommand()));
    }
}
