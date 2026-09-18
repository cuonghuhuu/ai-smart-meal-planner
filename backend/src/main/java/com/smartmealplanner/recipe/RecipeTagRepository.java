package com.smartmealplanner.recipe;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecipeTagRepository extends JpaRepository<RecipeTag, Long> {

    Optional<RecipeTag> findByCode(String code);

    boolean existsByCode(String code);

    List<RecipeTag> findAllByOrderByDisplayNameAscCodeAsc();

    @Query(value = """
            select tag.*
            from recipe_tags tag
            join recipe_tag_assignments assignment
              on assignment.tag_id = tag.id
            where assignment.recipe_id = :recipeId
            order by tag.display_name asc, tag.code asc
            """, nativeQuery = true)
    List<RecipeTag> findByRecipeIdOrderByDisplayNameAscCodeAsc(
            @Param("recipeId") Long recipeId);

    @Query("""
            select new com.smartmealplanner.recipe.RecipeTagAssignmentView(
                assignment.recipeId,
                tag.code,
                tag.displayName,
                tag.tagKind)
            from RecipeTagAssignment assignment, RecipeTag tag
            where assignment.tagId = tag.id
              and assignment.recipeId in :recipeIds
            order by assignment.recipeId asc, tag.displayName asc, tag.code asc
            """)
    List<RecipeTagAssignmentView> findByRecipeIdsOrderByDisplayNameAscCodeAsc(
            @Param("recipeIds") Collection<Long> recipeIds);
}
