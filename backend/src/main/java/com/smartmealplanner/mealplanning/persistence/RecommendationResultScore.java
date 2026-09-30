package com.smartmealplanner.mealplanning.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_result_scores")
public class RecommendationResultScore {
    @EmbeddedId
    private RecommendationResultScoreId id;
    @MapsId("resultId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private RecommendationResult result;
    @Column(name = "score_value", nullable = false, precision = 12, scale = 6)
    private BigDecimal scoreValue;
    @Column(precision = 10, scale = 4)
    private BigDecimal weight;

    protected RecommendationResultScore() { }

    public RecommendationResultScore(RecommendationResult result, Long scoreComponentId,
            BigDecimal scoreValue, BigDecimal weight) {
        if (result == null || result.id() == null || scoreValue == null) {
            throw new IllegalArgumentException("Persisted result and scoreValue are required");
        }
        this.id = new RecommendationResultScoreId(result.id(), scoreComponentId);
        this.result = result;
        this.scoreValue = scoreValue;
        this.weight = weight;
    }

    public RecommendationResultScoreId id() { return id; }
    public RecommendationResult result() { return result; }
    public BigDecimal scoreValue() { return scoreValue; }
    public BigDecimal weight() { return weight; }
}
