package com.smartmealplanner.nutrition.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface UserNutritionTargetValueRepository
        extends JpaRepository<
                UserNutritionTargetValue,
                UserNutritionTargetValueId> {

    List<UserNutritionTargetValue> findAllByTarget_Id(
            Long targetId);

    @Query("""
            select targetValue
            from UserNutritionTargetValue targetValue
            join fetch targetValue.nutrient nutrient
            join fetch nutrient.unit
            where targetValue.target.id = :targetId
            order by nutrient.code asc
            """)
    List<UserNutritionTargetValue>
            findAllByTargetIdWithNutrientAndUnit(
                    @Param("targetId")
                    Long targetId);

    @Query("""
            select targetValue
            from UserNutritionTargetValue targetValue
            join fetch targetValue.nutrient nutrient
            join fetch nutrient.unit
            where targetValue.target.id in :targetIds
            order by targetValue.target.id asc, nutrient.code asc
            """)
    List<UserNutritionTargetValue>
            findAllByTargetIdInWithNutrientAndUnit(
                    @Param("targetIds")
                    Collection<Long> targetIds);
}
