package com.smartmealplanner.nutrition.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface MeasurementUnitRepository
        extends JpaRepository<MeasurementUnit, Long> {

    Optional<MeasurementUnit> findByCode(
            String code);

    @Query("""
            select unit
            from MeasurementUnit unit
            left join fetch unit.baseUnit
            where unit.id in :ids
            """)
    List<MeasurementUnit> findAllWithBaseUnitByIdIn(
            @Param("ids") Collection<Long> ids);
}
