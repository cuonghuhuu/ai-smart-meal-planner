package com.smartmealplanner.food;

import java.util.Collection;
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

    @Query("""
            select serving from FoodServing serving
            join fetch serving.food food
            join fetch serving.unit unit
            left join fetch unit.baseUnit
            where serving.food.id in :foodIds
            order by serving.food.id asc, serving.unit.code asc,
                     serving.displayName asc
            """)
    List<FoodServing> findByFoodIdsWithUnit(
            @Param("foodIds") Collection<Long> foodIds);
}
