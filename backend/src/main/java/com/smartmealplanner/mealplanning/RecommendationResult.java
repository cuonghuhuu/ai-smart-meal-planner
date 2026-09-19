package com.smartmealplanner.mealplanning;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_results")
class RecommendationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "rank_position", nullable = false)
    private Short rankPosition;

    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "food_id")
    private Long foodId;

    @Column(name = "total_score", precision = 10, scale = 4)
    private BigDecimal totalScore;

    @Column(length = 500)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_decision", nullable = false, length = 20)
    private RecommendationUserDecision userDecision;

    @Column(name = "decided_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected RecommendationResult() {
    }

    RecommendationResult(Long requestId, int rankPosition, Long recipeId,
            BigDecimal totalScore, String explanation) {
        if (requestId == null || rankPosition < 1 || recipeId == null
                || totalScore == null || totalScore.scale() > 4
                || explanation == null || explanation.length() > 500) {
            throw new IllegalArgumentException("Invalid recommendation result");
        }
        this.requestId = requestId;
        this.rankPosition = toShort(rankPosition, "rankPosition");
        this.recipeId = recipeId;
        this.totalScore = totalScore;
        this.explanation = explanation;
        this.userDecision = RecommendationUserDecision.PENDING;
    }

    Long internalId() { return id; }
    Long requestId() { return requestId; }
    Short rankPosition() { return rankPosition; }
    Long recipeId() { return recipeId; }
    BigDecimal totalScore() { return totalScore; }
    String explanation() { return explanation; }

    private static Short toShort(int value, String field) {
        if (value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is out of range");
        }
        return (short) value;
    }
}
