package com.smartmealplanner.recipe;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MealSlotTypeRepository extends JpaRepository<MealSlotType, Long> {

    Optional<MealSlotType> findByCode(String code);

    boolean existsByCode(String code);

    List<MealSlotType> findAllByCodeIn(Collection<String> codes);

    List<MealSlotType> findAllByOrderByDisplayOrderAscCodeAsc();

    @Query(value = """
            select slot.*
            from meal_slot_types slot
            join recipe_meal_slot_types assignment
              on assignment.meal_slot_type_id = slot.id
            where assignment.recipe_id = :recipeId
            order by slot.display_order asc, slot.code asc
            """, nativeQuery = true)
    List<MealSlotType> findByRecipeIdOrderByDisplayOrderAscCodeAsc(
            @Param("recipeId") Long recipeId);

    @Query("""
            select new com.smartmealplanner.recipe.RecipeMealSlotAssignmentView(
                assignment.recipeId,
                slot.code,
                slot.displayName,
                slot.displayOrder,
                slot.typicalTime,
                slot.mainMeal)
            from RecipeMealSlotType assignment, MealSlotType slot
            where assignment.mealSlotTypeId = slot.id
              and assignment.recipeId in :recipeIds
            order by assignment.recipeId asc, slot.displayOrder asc, slot.code asc
            """)
    List<RecipeMealSlotAssignmentView> findByRecipeIdsOrderByDisplayOrderAscCodeAsc(
            @Param("recipeIds") Collection<Long> recipeIds);
}
