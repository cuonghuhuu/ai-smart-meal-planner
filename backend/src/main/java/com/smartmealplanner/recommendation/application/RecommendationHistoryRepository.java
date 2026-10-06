package com.smartmealplanner.recommendation.application;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.smartmealplanner.recommendation.persistence.RecommendationResult;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Recommendation-owned bounded read of persisted recent Recipe exposure history. */
interface RecommendationHistoryRepository extends Repository<RecommendationResult, Long> {

    @Query(value = """
            SELECT LOWER(HEX(recipe.public_id)) AS recipeHex,
                   COUNT(*) AS recommendationCount
            FROM recommendation_results result
            JOIN recommendation_requests request ON request.id = result.request_id
            JOIN recipes recipe ON recipe.id = result.recipe_id
            WHERE request.user_id = :userId
              AND request.request_kind IN ('MEAL_PLAN', 'RECIPE_SUGGESTION')
              AND request.status IN ('SUCCEEDED', 'DEGRADED')
              AND request.completed_at IS NOT NULL
              AND request.completed_at >= :since
              AND result.recipe_id IS NOT NULL
              AND LOWER(HEX(recipe.public_id)) IN (:recipeHexIds)
            GROUP BY recipe.public_id
            """, nativeQuery = true)
    List<RecentRecipeCountProjection> countRecentRecipeUses(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("recipeHexIds") Collection<String> recipeHexIds);

    interface RecentRecipeCountProjection {
        String getRecipeHex();
        Long getRecommendationCount();
    }
}
