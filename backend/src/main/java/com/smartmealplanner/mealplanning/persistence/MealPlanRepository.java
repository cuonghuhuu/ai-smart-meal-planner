package com.smartmealplanner.mealplanning.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    Optional<MealPlan> findBySourceRequestId(Long sourceRequestId);

    boolean existsBySourceRequestId(Long sourceRequestId);
}
