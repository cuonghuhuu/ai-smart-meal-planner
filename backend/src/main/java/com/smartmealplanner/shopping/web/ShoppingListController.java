package com.smartmealplanner.shopping.web;

import java.util.UUID;

import com.smartmealplanner.shared.web.SecurityPrincipals;
import com.smartmealplanner.shopping.application.ShoppingListQueryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP adapter for meal-plan shopping projections. */
@RestController
@RequestMapping("/api/v1/me/meal-plans")
public class ShoppingListController {

    private final ShoppingListQueryService shoppingLists;

    public ShoppingListController(ShoppingListQueryService shoppingLists) {
        this.shoppingLists = shoppingLists;
    }

    @GetMapping("/{mealPlanPublicId}/shopping-list")
    public ShoppingListResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID mealPlanPublicId) {
        return ShoppingListResponse.from(shoppingLists.get(
                SecurityPrincipals.authenticatedPublicId(jwt),
                mealPlanPublicId));
    }
}
