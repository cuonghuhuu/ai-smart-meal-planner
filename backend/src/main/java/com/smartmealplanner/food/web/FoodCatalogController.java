package com.smartmealplanner.food.web;

import java.util.List;
import java.util.UUID;

import com.smartmealplanner.food.FoodCatalogService;
import com.smartmealplanner.food.UserDislikedIngredientService;
import com.smartmealplanner.shared.web.SecurityPrincipals;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for authenticated Food catalog reads and private dislikes. */
@RestController
public class FoodCatalogController {
    private final FoodCatalogService foods;
    private final UserDislikedIngredientService dislikes;
    FoodCatalogController(FoodCatalogService foods, UserDislikedIngredientService dislikes) { this.foods = foods; this.dislikes = dislikes; }
    @GetMapping("/api/v1/foods")
    public FoodResponse.Page getFoods(@RequestParam(required = false) String q, @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return FoodWebMapper.page(foods.getFoods(q, categoryCode, page, size));
    }
    @GetMapping("/api/v1/foods/{publicId}")
    public FoodResponse.Detail getFood(@org.springframework.web.bind.annotation.PathVariable UUID publicId) { return FoodWebMapper.food(foods.getFood(publicId)); }
    @GetMapping("/api/v1/reference/food-categories")
    public List<FoodResponse.Category> categories() { return foods.getFoodCategories().stream().map(FoodWebMapper::category).toList(); }
    @GetMapping("/api/v1/me/disliked-ingredients")
    public List<IngredientResponse.Disliked> getDislikes(@AuthenticationPrincipal Jwt jwt) { return FoodWebMapper.dislikes(dislikes.getPreferences(SecurityPrincipals.authenticatedPublicId(jwt))); }
    @PutMapping("/api/v1/me/disliked-ingredients")
    public List<IngredientResponse.Disliked> replaceDislikes(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReplaceDislikedIngredientsRequest request) {
        UUID publicId = SecurityPrincipals.authenticatedPublicId(jwt);
        return FoodWebMapper.dislikes(dislikes.replacePreferences(publicId, FoodWebMapper.drafts(request)));
    }
}
