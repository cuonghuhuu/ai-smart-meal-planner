package com.smartmealplanner.recipe;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface RecipeRepository extends JpaRepository<Recipe, Long> {

    @Query(value = """
            select r.*
            from recipes r
            where r.status = 'PUBLISHED'
              and (:query is null
                   or match(r.title, r.summary)
                      against(:query in natural language mode))
              and (:mealSlotCode is null
                   or exists (
                       select 1
                       from recipe_meal_slot_types rms
                       join meal_slot_types mst
                         on mst.id = rms.meal_slot_type_id
                       where rms.recipe_id = r.id
                         and mst.code = :mealSlotCode
                   )
                   or not exists (
                       select 1
                       from recipe_meal_slot_types rms_any
                       where rms_any.recipe_id = r.id
                   ))
              and (:tagCode is null
                   or exists (
                       select 1
                       from recipe_tag_assignments rta
                       join recipe_tags rt on rt.id = rta.tag_id
                       where rta.recipe_id = r.id
                         and rt.code = :tagCode
                   ))
              and (:maxMinutes is null or r.total_minutes <= :maxMinutes)
            order by
              case when :query is null then 0
                   else match(r.title, r.summary)
                        against(:query in natural language mode)
              end desc,
              r.published_at desc,
              r.public_id asc
            """, countQuery = """
            select count(*)
            from recipes r
            where r.status = 'PUBLISHED'
              and (:query is null
                   or match(r.title, r.summary)
                      against(:query in natural language mode))
              and (:mealSlotCode is null
                   or exists (
                       select 1
                       from recipe_meal_slot_types rms
                       join meal_slot_types mst
                         on mst.id = rms.meal_slot_type_id
                       where rms.recipe_id = r.id
                         and mst.code = :mealSlotCode
                   )
                   or not exists (
                       select 1
                       from recipe_meal_slot_types rms_any
                       where rms_any.recipe_id = r.id
                   ))
              and (:tagCode is null
                   or exists (
                       select 1
                       from recipe_tag_assignments rta
                       join recipe_tags rt on rt.id = rta.tag_id
                       where rta.recipe_id = r.id
                         and rt.code = :tagCode
                   ))
              and (:maxMinutes is null or r.total_minutes <= :maxMinutes)
            """, nativeQuery = true)
    Page<Recipe> findPublished(
            @Param("query") String query,
            @Param("mealSlotCode") String mealSlotCode,
            @Param("tagCode") String tagCode,
            @Param("maxMinutes") Integer maxMinutes,
            Pageable pageable);

    @Query(value = """
            select *
            from recipes
            where public_id = :publicId
              and status = 'PUBLISHED'
            """, nativeQuery = true)
    Optional<Recipe> findPublishedByPublicId(@Param("publicId") byte[] publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select recipe
            from Recipe recipe
            where recipe.publicId = :publicId
            """)
    Optional<Recipe> findByPublicIdForUpdate(
            @Param("publicId") byte[] publicId);
}
