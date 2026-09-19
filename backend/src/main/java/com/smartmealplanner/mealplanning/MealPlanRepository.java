package com.smartmealplanner.mealplanning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    List<MealPlan> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    Optional<MealPlan> findByPublicIdAndUserId(byte[] publicId, Long userId);
}
