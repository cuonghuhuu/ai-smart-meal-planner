package com.smartmealplanner.nutrition.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NutrientRepository
        extends JpaRepository<Nutrient, Long> {

    Optional<Nutrient> findByCode(
            String code);

    List<Nutrient> findAllByOrderByDisplayOrderAscCodeAsc();

    @Query("""
            select nutrient
            from Nutrient nutrient
            join fetch nutrient.unit
            order by nutrient.displayOrder asc, nutrient.code asc
            """)
    List<Nutrient> findAllWithUnitOrderByDisplayOrderAscCodeAsc();

    @Query("""
            select nutrient
            from Nutrient nutrient
            join fetch nutrient.unit
            where nutrient.id in :ids
            """)
    List<Nutrient> findAllWithUnitByIdIn(@Param("ids") Collection<Long> ids);
}
