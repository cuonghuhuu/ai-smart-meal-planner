package com.smartmealplanner.profile.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository
        extends JpaRepository<UserProfile, Long> {

    @Query("""
            select profile
            from UserProfile profile
            left join fetch profile.activityLevel
            left join fetch profile.nutritionGoal
            where profile.userId = :userId
            """)
    Optional<UserProfile> findByUserIdWithReferences(
            @Param("userId")
            Long userId);
}
