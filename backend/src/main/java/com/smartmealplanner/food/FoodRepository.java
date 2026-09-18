package com.smartmealplanner.food;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

interface FoodRepository extends JpaRepository<Food, Long> {
    Optional<Food> findByCode(String code);

    Optional<Food> findByPublicId(byte[] publicId);

    @Query("""
            select food from Food food
            left join fetch food.category
            where food.publicId = :publicId and food.active = true
            """)
    Optional<Food> findActiveSummaryByPublicId(
            @Param("publicId") byte[] publicId);

    @Query("""
            select food from Food food
            left join fetch food.category
            where food.active = true
              and (:categoryCode is null or food.category.code = :categoryCode)
            """)
    Page<Food> findActiveByCategory(
            @Param("categoryCode") String categoryCode,
            Pageable pageable);

    @Query(value = """
            select f.* from foods f
            left join food_categories c on c.id = f.food_category_id
            where f.is_active = true
              and (:categoryCode is null or c.code = :categoryCode)
              and match(f.display_name, f.brand) against(:query in natural language mode)
            order by match(f.display_name, f.brand) against(:query in natural language mode) desc,
                     f.display_name asc, f.public_id asc
            """, countQuery = """
            select count(*) from foods f
            left join food_categories c on c.id = f.food_category_id
            where f.is_active = true
              and (:categoryCode is null or c.code = :categoryCode)
              and match(f.display_name, f.brand) against(:query in natural language mode)
            """, nativeQuery = true)
    Page<Food> searchActive(
            @Param("query") String query,
            @Param("categoryCode") String categoryCode,
            Pageable pageable);
}
