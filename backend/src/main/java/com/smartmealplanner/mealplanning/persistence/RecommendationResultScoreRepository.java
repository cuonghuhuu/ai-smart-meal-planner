package com.smartmealplanner.mealplanning.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationResultScoreRepository
        extends JpaRepository<RecommendationResultScore, RecommendationResultScoreId> { }
