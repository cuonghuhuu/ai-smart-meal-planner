package com.smartmealplanner.food;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IngredientFoodRepository extends JpaRepository<IngredientFood, IngredientFoodId> {

    @Query("""
            select case when count(mapping) > 0 then true else false end
            from IngredientFood mapping
            where mapping.ingredient.id = :ingredientId
              and mapping.food.id = :foodId
            """)
    boolean existsByIngredientIdAndFoodId(
            @Param("ingredientId") Long ingredientId,
            @Param("foodId") Long foodId);

    @Query("""
            select mapping from IngredientFood mapping
            join fetch mapping.food food
            left join fetch food.category
            where mapping.ingredient.id = :ingredientId
            order by mapping.primary desc, food.displayName asc, food.publicId asc
            """)
    List<IngredientFood> findByIngredientIdWithFood(@Param("ingredientId") Long ingredientId);

    @Query("""
            select mapping
            from IngredientFood mapping
            join fetch mapping.ingredient ingredient
            join fetch mapping.food food
            where mapping.ingredient.id in :ingredientIds
            order by mapping.ingredient.id asc, mapping.primary desc,
                     food.displayName asc
            """)
    List<IngredientFood> findByIngredientIdsWithFood(
            @Param("ingredientIds") Collection<Long> ingredientIds);
}
