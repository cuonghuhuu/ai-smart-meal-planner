package com.smartmealplanner.food;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserDislikedIngredientRepository extends JpaRepository<UserDislikedIngredient, UserDislikedIngredientId> {
    @Query("""
            select preference from UserDislikedIngredient preference
            join fetch preference.ingredient ingredient
            left join fetch ingredient.category
            where preference.userId = :userId
            order by ingredient.displayName asc, ingredient.publicId asc
            """)
    List<UserDislikedIngredient> findByUserIdWithIngredient(@Param("userId") Long userId);

    @Modifying
    @Query("delete from UserDislikedIngredient preference where preference.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
