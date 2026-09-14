package com.smartmealplanner.food;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FoodServingRepository extends JpaRepository<FoodServing, Long> {
    @Query("""
            select serving from FoodServing serving
            join fetch serving.unit
            where serving.food.id = :foodId
            order by serving.defaultServing desc, serving.displayName asc
            """)
    List<FoodServing> findByFoodIdWithUnit(@Param("foodId") Long foodId);
}
