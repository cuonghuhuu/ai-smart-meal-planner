package com.smartmealplanner.recipe;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecipeNutritionValueRepository
        extends JpaRepository<RecipeNutritionValue, RecipeNutritionValueId> {

    @Query(value = """
            select nutrition_value.*
            from recipe_nutrition_values nutrition_value
            left join nutrients nutrient on nutrient.id = nutrition_value.nutrient_id
            where nutrition_value.snapshot_id = :snapshotId
            order by nutrient.display_order asc, nutrient.code asc
            """, nativeQuery = true)
    List<RecipeNutritionValue> findBySnapshotIdOrderByNutrient(
            @Param("snapshotId") Long snapshotId);
}
