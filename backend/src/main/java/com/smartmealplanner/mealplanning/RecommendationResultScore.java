package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_result_scores")
class RecommendationResultScore {

    @EmbeddedId
    private RecommendationResultScoreId id;

    @Column(name = "score_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal scoreValue;

    @Column(precision = 10, scale = 4)
    private BigDecimal weight;

    protected RecommendationResultScore() {
    }

    RecommendationResultScore(Long resultId, Long componentId,
            BigDecimal scoreValue, BigDecimal weight) {
        if (resultId == null || componentId == null || scoreValue == null
                || scoreValue.signum() < 0 || scoreValue.compareTo(BigDecimal.ONE) > 0
                || weight == null || weight.signum() < 0) {
            throw new IllegalArgumentException("Invalid recommendation score");
        }
        this.id = new RecommendationResultScoreId(resultId, componentId);
        this.scoreValue = scoreValue;
        this.weight = weight;
    }
}
