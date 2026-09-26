package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecipeMealSlotTypeRepository
        extends JpaRepository<RecipeMealSlotType, RecipeMealSlotTypeId> {

    List<RecipeMealSlotType> findByRecipeId(Long recipeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RecipeMealSlotType assignment "
            + "where assignment.recipeId = :recipeId")
    void deleteByRecipeId(@Param("recipeId") Long recipeId);
}
