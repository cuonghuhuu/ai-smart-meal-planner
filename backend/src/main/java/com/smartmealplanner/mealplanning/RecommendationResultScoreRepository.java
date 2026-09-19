package com.smartmealplanner.mealplanning;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecommendationResultScoreRepository
        extends JpaRepository<RecommendationResultScore, RecommendationResultScoreId> {
}
