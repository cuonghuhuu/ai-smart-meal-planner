package com.smartmealplanner.food;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long> {
    @Query("""
            select alias from IngredientAlias alias
            join fetch alias.ingredient ingredient
            where alias.alias = :alias and ingredient.active = true
            """)
    Optional<IngredientAlias> findActiveByAlias(@Param("alias") String alias);

    @Query("""
            select alias from IngredientAlias alias
            where alias.ingredient.id = :ingredientId
            order by alias.alias asc
            """)
    List<IngredientAlias> findByIngredientIdOrderByAlias(@Param("ingredientId") Long ingredientId);
}
