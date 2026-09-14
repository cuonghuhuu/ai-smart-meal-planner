package com.smartmealplanner.food;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FoodCategoryRepository extends JpaRepository<FoodCategory, Long> {
    Optional<FoodCategory> findByCode(String code);

    @Query("""
            select category from FoodCategory category
            left join fetch category.parentCategory
            order by category.displayName asc, category.code asc
            """)
    List<FoodCategory> findAllWithParentOrderByDisplayNameAscCodeAsc();
}
