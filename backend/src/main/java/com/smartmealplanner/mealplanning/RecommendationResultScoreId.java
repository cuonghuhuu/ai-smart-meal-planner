package com.smartmealplanner.mealplanning;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class RecommendationResultScoreId implements Serializable {
    @Column(name = "result_id")
    private Long resultId;
    @Column(name = "score_component_id")
    private Long scoreComponentId;

    protected RecommendationResultScoreId() {
    }

    RecommendationResultScoreId(Long resultId, Long scoreComponentId) {
        this.resultId = resultId;
        this.scoreComponentId = scoreComponentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecommendationResultScoreId that)) return false;
        return Objects.equals(resultId, that.resultId)
                && Objects.equals(scoreComponentId, that.scoreComponentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultId, scoreComponentId);
    }
}
