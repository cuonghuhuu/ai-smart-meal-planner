package com.smartmealplanner.mealplanning;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntry, Long> {
    @Query("""
            select entry
            from MealPlanEntry entry
            where entry.mealPlanId = :mealPlanId
            order by entry.planDate asc, entry.mealSlotTypeId asc, entry.positionInSlot asc
            """)
    List<MealPlanEntry> findByMealPlanId(@Param("mealPlanId") Long mealPlanId);
}
