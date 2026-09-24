package com.smartmealplanner.food;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IngredientAllergenRepository extends JpaRepository<IngredientAllergen, IngredientAllergenId> {
    @Query("""
            select fact from IngredientAllergen fact
            where fact.ingredient.id in :ingredientIds
            order by fact.ingredient.id asc, fact.allergenId asc
            """)
    List<IngredientAllergen> findByIngredientIds(
            @Param("ingredientIds") java.util.Collection<Long> ingredientIds);

    @Query("""
            select fact from IngredientAllergen fact
            where fact.ingredient.id = :ingredientId
            order by fact.allergenId asc
            """)
    List<IngredientAllergen> findByIngredientId(@Param("ingredientId") Long ingredientId);
}
