package com.smartmealplanner.mealplanning.web;

import java.util.UUID;

import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP adapter for persisted meal-plan reads. */
@RestController
@RequestMapping("/api/v1/me/meal-plans")
public class MealPlanQueryController {
    private final PersistedMealPlanQueryService plans;

    public MealPlanQueryController(PersistedMealPlanQueryService plans) {
        this.plans = plans;
    }

    @GetMapping("/{mealPlanPublicId}")
    public MealPlanDetailResponse get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID mealPlanPublicId) {
        return MealPlanDetailResponse.from(plans.get(
                SecurityPrincipals.authenticatedPublicId(jwt), mealPlanPublicId));
    }
}
