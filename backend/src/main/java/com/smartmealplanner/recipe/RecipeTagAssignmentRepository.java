package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecipeTagAssignmentRepository
        extends JpaRepository<RecipeTagAssignment, RecipeTagAssignmentId> {

    List<RecipeTagAssignment> findByRecipeId(Long recipeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecipeTagAssignment assignment "
            + "where assignment.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);
}
