package com.smartmealplanner.food;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FoodRepository extends JpaRepository<Food, Long> {
    Optional<Food> findByPublicId(byte[] publicId);
}
