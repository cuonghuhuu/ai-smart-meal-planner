package com.smartmealplanner.food;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByCode(String code);

    Optional<Ingredient> findByPublicId(byte[] publicId);

    @Query("""
            select ingredient from Ingredient ingredient
            left join fetch ingredient.category
            left join fetch ingredient.defaultFood
            left join fetch ingredient.defaultUnit
            where ingredient.publicId = :publicId and ingredient.active = true
            """)
    Optional<Ingredient> findActiveSummaryByPublicId(@Param("publicId") byte[] publicId);

    @Query("""
            select ingredient from Ingredient ingredient
            left join fetch ingredient.category
            where ingredient.active = true
              and (:categoryCode is null or ingredient.category.code = :categoryCode)
            """)
    Page<Ingredient> findActiveByCategory(@Param("categoryCode") String categoryCode, Pageable pageable);

    @Query(value = """
            select i.* from ingredients i
            left join food_categories c on c.id = i.food_category_id
            where i.is_active = true
              and (:categoryCode is null or c.code = :categoryCode)
              and match(i.display_name) against(:query in natural language mode)
            order by match(i.display_name) against(:query in natural language mode) desc,
                     i.display_name asc, i.public_id asc
            """, countQuery = """
            select count(*) from ingredients i
            left join food_categories c on c.id = i.food_category_id
            where i.is_active = true
              and (:categoryCode is null or c.code = :categoryCode)
              and match(i.display_name) against(:query in natural language mode)
            """, nativeQuery = true)
    Page<Ingredient> searchActive(@Param("query") String query,
            @Param("categoryCode") String categoryCode, Pageable pageable);

    @Query("""
            select ingredient from Ingredient ingredient
            where ingredient.active = true and ingredient.publicId in :publicIds
            """)
    List<Ingredient> findActiveByPublicIdIn(@Param("publicIds") Collection<byte[]> publicIds);

    @Query("""
            select ingredient
            from Ingredient ingredient
            left join fetch ingredient.defaultFood
            where ingredient.id in :ids
            """)
    List<Ingredient> findAllWithDefaultFoodByIdIn(
            @Param("ids") Collection<Long> ids);
}
