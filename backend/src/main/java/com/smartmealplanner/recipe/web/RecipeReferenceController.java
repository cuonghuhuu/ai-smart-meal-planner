package com.smartmealplanner.recipe.web;

import java.util.List;

import com.smartmealplanner.recipe.RecipeCatalogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated read endpoints for Recipe reference vocabularies. */
@RestController
@RequestMapping("/api/v1/reference")
public class RecipeReferenceController {

    private final RecipeCatalogService recipes;

    public RecipeReferenceController(RecipeCatalogService recipes) {
        this.recipes = recipes;
    }

    @GetMapping("/recipe-tags")
    public List<RecipeResponse.Tag> getRecipeTags() {
        return recipes.getRecipeTags().stream()
                .map(RecipeWebMapper::tag)
                .toList();
    }

    @GetMapping("/meal-slot-types")
    public List<RecipeResponse.MealSlot> getMealSlotTypes() {
        return recipes.getMealSlotTypes().stream()
                .map(RecipeWebMapper::mealSlot)
                .toList();
    }
}
