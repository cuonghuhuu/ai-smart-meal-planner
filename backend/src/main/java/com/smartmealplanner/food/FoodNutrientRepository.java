package com.smartmealplanner.food;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FoodNutrientRepository extends JpaRepository<FoodNutrient, FoodNutrientId> {
    @Query("""
            select fact from FoodNutrient fact
            join fetch fact.nutrient nutrient
            join fetch nutrient.unit
            where fact.food.id = :foodId
            order by nutrient.displayOrder asc, nutrient.code asc
            """)
    List<FoodNutrient> findByFoodIdWithNutrientAndUnit(@Param("foodId") Long foodId);
}
