package com.smartmealplanner.recipe;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecipeNutritionSnapshotRepository
        extends JpaRepository<RecipeNutritionSnapshot, Long> {

    @Query("""
            select snapshot
            from RecipeNutritionSnapshot snapshot
            where snapshot.recipeId = :recipeId
              and snapshot.current = true
            """)
    Optional<RecipeNutritionSnapshot> findCurrentByRecipeId(
            @Param("recipeId") Long recipeId);

    @Query("""
            select snapshot
            from RecipeNutritionSnapshot snapshot
            where snapshot.recipeId in :recipeIds
              and snapshot.current = true
            """)
    List<RecipeNutritionSnapshot> findCurrentByRecipeIdIn(
            @Param("recipeIds") Collection<Long> recipeIds);
}
