package com.smartmealplanner.recipe;

import java.util.List;
import java.util.Collection;

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

    @Query(value = """
            select nutrition_value.*
            from recipe_nutrition_values nutrition_value
            where nutrition_value.snapshot_id in (:snapshotIds)
            order by nutrition_value.snapshot_id asc, nutrition_value.nutrient_id asc
            """, nativeQuery = true)
    List<RecipeNutritionValue> findBySnapshotIdIn(
            @Param("snapshotIds") Collection<Long> snapshotIds);
}
