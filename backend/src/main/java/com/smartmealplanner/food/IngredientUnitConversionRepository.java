package com.smartmealplanner.food;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IngredientUnitConversionRepository extends JpaRepository<IngredientUnitConversion, Long> {
    @Query("""
            select conversion from IngredientUnitConversion conversion
            join fetch conversion.fromUnit
            join fetch conversion.toUnit
            where conversion.ingredient.id = :ingredientId
            order by conversion.fromUnit.code asc, conversion.toUnit.code asc
            """)
    List<IngredientUnitConversion> findByIngredientIdWithUnits(@Param("ingredientId") Long ingredientId);

    @Query("""
            select conversion from IngredientUnitConversion conversion
            join fetch conversion.fromUnit
            join fetch conversion.toUnit
            where conversion.ingredient.id = :ingredientId
              and conversion.fromUnit.code = :fromCode
              and conversion.toUnit.code = :toCode
            """)
    Optional<IngredientUnitConversion> findByIngredientAndUnitCodes(
            @Param("ingredientId") Long ingredientId,
            @Param("fromCode") String fromCode,
            @Param("toCode") String toCode);
}
