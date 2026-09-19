package com.smartmealplanner.mealplanning.web;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.MealPlanService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated owner-scoped adapter for generated meal plans. */
@RestController
@RequestMapping("/api/v1/me/meal-plans")
public class MealPlanController {
    private final MealPlanService mealPlans;

    public MealPlanController(MealPlanService mealPlans) {
        this.mealPlans = mealPlans;
    }

    @PostMapping("/generate")
    public MealPlanResponse.Generation generate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MealPlanGenerationRequest request) {
        return mealPlans.generate(
                SecurityPrincipals.authenticatedPublicId(jwt), request);
    }

    @GetMapping
    public List<MealPlanResponse.Item> list(@AuthenticationPrincipal Jwt jwt) {
        return mealPlans.list(SecurityPrincipals.authenticatedPublicId(jwt));
    }

    @GetMapping("/{publicId}")
    public MealPlanResponse.Detail get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId) {
        return mealPlans.get(SecurityPrincipals.authenticatedPublicId(jwt), publicId);
    }

    @PostMapping("/{publicId}/accept")
    public MealPlanResponse.Detail accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId) {
        return mealPlans.accept(SecurityPrincipals.authenticatedPublicId(jwt), publicId);
    }
}
