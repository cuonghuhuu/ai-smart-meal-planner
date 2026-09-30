package com.smartmealplanner.mealplanning.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> { }
