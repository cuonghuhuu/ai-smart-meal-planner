package com.smartmealplanner.food.web;

import java.util.UUID;

import com.smartmealplanner.food.IngredientCatalogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for authenticated Ingredient catalog reads. */
@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientCatalogController {
    private final IngredientCatalogService ingredients;
    IngredientCatalogController(IngredientCatalogService ingredients) { this.ingredients = ingredients; }
    @GetMapping
    public IngredientResponse.Page getIngredients(@RequestParam(required = false) String q, @RequestParam(required = false) String categoryCode,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return FoodWebMapper.page(ingredients.getIngredients(q, categoryCode, page, size));
    }
    @GetMapping("/{publicId}")
    public IngredientResponse.Detail getIngredient(@PathVariable UUID publicId) { return FoodWebMapper.ingredient(ingredients.getIngredient(publicId)); }
}
