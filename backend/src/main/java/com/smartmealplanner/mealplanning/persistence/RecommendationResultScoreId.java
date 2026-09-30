package com.smartmealplanner.mealplanning.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RecommendationResultScoreId implements Serializable {
    @Column(name = "result_id")
    private Long resultId;
    @Column(name = "score_component_id")
    private Long scoreComponentId;

    protected RecommendationResultScoreId() { }

    public RecommendationResultScoreId(Long resultId, Long scoreComponentId) {
        this.resultId = Objects.requireNonNull(resultId);
        this.scoreComponentId = Objects.requireNonNull(scoreComponentId);
    }

    public Long resultId() { return resultId; }
    public Long scoreComponentId() { return scoreComponentId; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof RecommendationResultScoreId that
                && Objects.equals(resultId, that.resultId)
                && Objects.equals(scoreComponentId, that.scoreComponentId);
    }

    @Override public int hashCode() { return Objects.hash(resultId, scoreComponentId); }
}
