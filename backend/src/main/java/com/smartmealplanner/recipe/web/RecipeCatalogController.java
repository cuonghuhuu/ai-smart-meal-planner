package com.smartmealplanner.recipe.web;

import java.util.UUID;

import com.smartmealplanner.recipe.RecipeCatalogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for authenticated, read-only published Recipe catalog reads. */
@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeCatalogController {

    private final RecipeCatalogService recipes;

    public RecipeCatalogController(RecipeCatalogService recipes) {
        this.recipes = recipes;
    }

    @GetMapping
    public RecipeResponse.Page getRecipes(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String mealSlotCode,
            @RequestParam(required = false) String tagCode,
            @RequestParam(required = false) Integer maxMinutes,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return RecipeWebMapper.page(recipes.getRecipes(
                q, mealSlotCode, tagCode, maxMinutes, page, size));
    }

    @GetMapping("/{publicId}")
    public RecipeResponse.Detail getRecipe(@PathVariable UUID publicId) {
        return RecipeWebMapper.detail(recipes.getRecipe(publicId));
    }
}
