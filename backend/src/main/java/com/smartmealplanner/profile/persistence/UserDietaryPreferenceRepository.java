package com.smartmealplanner.profile.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDietaryPreferenceRepository
        extends JpaRepository<UserDietaryPreference, UserDietaryPreferenceId> {

    @Query("""
            select udp
            from UserDietaryPreference udp
            join fetch udp.preference
            where udp.userId = :userId
            order by udp.preference.displayOrder asc, udp.preference.code asc
            """)
    List<UserDietaryPreference> findByUserIdWithPreference(
            @Param("userId")
            Long userId);

    @Modifying
    @Query("""
            delete from UserDietaryPreference udp
            where udp.userId = :userId
            """)
    void deleteByUserId(
            @Param("userId")
            Long userId);
}
