package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    List<RecipeIngredient> findByRecipeIdOrderByLineNumberAsc(Long recipeId);
}
