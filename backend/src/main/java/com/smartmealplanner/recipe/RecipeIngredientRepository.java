package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    @Query("""
            select line from RecipeIngredient line
            where line.recipeId in :recipeIds
            order by line.recipeId asc, line.lineNumber asc
            """)
    List<RecipeIngredient> findByRecipeIds(
            @Param("recipeIds") java.util.Collection<Long> recipeIds);

    List<RecipeIngredient> findByRecipeIdOrderByLineNumberAsc(Long recipeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecipeIngredient line where line.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);
}
