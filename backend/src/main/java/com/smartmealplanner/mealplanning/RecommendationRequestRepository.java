package com.smartmealplanner.mealplanning;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {
    Optional<RecommendationRequest> findByPublicIdAndUserId(byte[] publicId, Long userId);
}
