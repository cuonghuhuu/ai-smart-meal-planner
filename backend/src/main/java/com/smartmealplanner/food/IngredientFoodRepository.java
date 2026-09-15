package com.smartmealplanner.food;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IngredientFoodRepository extends JpaRepository<IngredientFood, IngredientFoodId> {
    @Query("""
            select mapping from IngredientFood mapping
            join fetch mapping.food food
            left join fetch food.category
            where mapping.ingredient.id = :ingredientId
            order by mapping.primary desc, food.displayName asc, food.publicId asc
            """)
    List<IngredientFood> findByIngredientIdWithFood(@Param("ingredientId") Long ingredientId);
}
