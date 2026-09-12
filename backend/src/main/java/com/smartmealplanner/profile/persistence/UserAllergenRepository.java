package com.smartmealplanner.profile.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAllergenRepository
        extends JpaRepository<UserAllergen, UserAllergenId> {

    @Query("""
            select ua
            from UserAllergen ua
            join fetch ua.allergen
            where ua.userId = :userId
            order by ua.allergen.displayOrder asc, ua.allergen.code asc
            """)
    List<UserAllergen> findByUserIdWithAllergen(
            @Param("userId")
            Long userId);

    @Modifying
    @Query("""
            delete from UserAllergen ua
            where ua.userId = :userId
            """)
    void deleteByUserId(
            @Param("userId")
            Long userId);
}
