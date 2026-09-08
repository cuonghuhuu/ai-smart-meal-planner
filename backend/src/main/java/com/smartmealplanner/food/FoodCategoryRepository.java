package com.smartmealplanner.food;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FoodCategoryRepository extends JpaRepository<FoodCategory, Long> {
    Optional<FoodCategory> findByCode(String code);
}
