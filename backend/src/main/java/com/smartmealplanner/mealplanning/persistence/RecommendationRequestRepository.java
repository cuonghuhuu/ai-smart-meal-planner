package com.smartmealplanner.mealplanning.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, Long> {
    Optional<RecommendationRequest> findByPublicId(byte[] publicId);
}
