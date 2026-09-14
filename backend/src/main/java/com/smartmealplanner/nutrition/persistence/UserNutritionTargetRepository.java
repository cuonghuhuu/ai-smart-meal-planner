package com.smartmealplanner.nutrition.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNutritionTargetRepository
        extends JpaRepository<UserNutritionTarget, Long> {

    Optional<UserNutritionTarget> findByUserIdAndEffectiveFrom(
            Long userId,
            LocalDate effectiveFrom);

    List<UserNutritionTarget> findByUserIdOrderByEffectiveFromDesc(
            Long userId);

    Page<UserNutritionTarget>
            findByUserIdOrderByEffectiveFromDescIdDesc(
                    Long userId,
                    Pageable pageable);

    @Query("""
            select target
            from UserNutritionTarget target
            where target.userId = :userId
              and target.effectiveFrom <= :effectiveOn
              and (target.effectiveTo is null
                   or target.effectiveTo >= :effectiveOn)
            order by target.effectiveFrom desc
            """)
    List<UserNutritionTarget> findByUserIdAndEffectiveOn(
            @Param("userId")
            Long userId,
            @Param("effectiveOn")
            LocalDate effectiveOn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select target
            from UserNutritionTarget target
            where target.userId = :userId
            order by target.effectiveFrom asc
            """)
    List<UserNutritionTarget> findAllByUserIdForUpdate(
            @Param("userId")
            Long userId);
}
