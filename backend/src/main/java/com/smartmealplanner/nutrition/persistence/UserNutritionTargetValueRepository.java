package com.smartmealplanner.nutrition.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNutritionTargetValueRepository
        extends JpaRepository<
                UserNutritionTargetValue,
                UserNutritionTargetValueId> {

    List<UserNutritionTargetValue> findAllByTarget_Id(
            Long targetId);
}
