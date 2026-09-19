package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecipeStepRepository extends JpaRepository<RecipeStep, RecipeStepId> {
    List<RecipeStep> findByRecipeIdOrderByStepNumberAsc(Long recipeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecipeStep step where step.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);
}
