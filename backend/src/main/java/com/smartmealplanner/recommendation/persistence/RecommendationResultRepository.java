package com.smartmealplanner.recommendation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, Long> {
    boolean existsByRequestId(Long requestId);
}
