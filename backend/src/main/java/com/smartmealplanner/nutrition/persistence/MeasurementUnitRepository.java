package com.smartmealplanner.nutrition.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementUnitRepository
        extends JpaRepository<MeasurementUnit, Long> {

    Optional<MeasurementUnit> findByCode(
            String code);
}
