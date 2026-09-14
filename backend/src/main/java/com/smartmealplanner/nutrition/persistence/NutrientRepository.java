package com.smartmealplanner.nutrition.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NutrientRepository
        extends JpaRepository<Nutrient, Long> {

    Optional<Nutrient> findByCode(
            String code);

    List<Nutrient> findAllByOrderByDisplayOrderAscCodeAsc();
}
