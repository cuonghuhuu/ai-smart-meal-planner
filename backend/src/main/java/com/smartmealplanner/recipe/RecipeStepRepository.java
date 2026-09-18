package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecipeStepRepository extends JpaRepository<RecipeStep, RecipeStepId> {
    List<RecipeStep> findByRecipeIdOrderByStepNumberAsc(Long recipeId);
}
