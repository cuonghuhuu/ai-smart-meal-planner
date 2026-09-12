package com.smartmealplanner.profile.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionGoalRepository
        extends JpaRepository<NutritionGoal, Long> {

    Optional<NutritionGoal> findByCode(
            String code);

    List<NutritionGoal> findAllByOrderByDisplayOrderAscCodeAsc();
}
