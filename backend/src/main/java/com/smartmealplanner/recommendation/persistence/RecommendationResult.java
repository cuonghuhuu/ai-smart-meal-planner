package com.smartmealplanner.recommendation.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendation_results")
public class RecommendationResult {
    public enum Decision { PENDING, ACCEPTED, REJECTED, IGNORED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private RecommendationRequest request;
    @Column(name = "rank_position", nullable = false)
    private Short rankPosition;
    @Column(name = "recipe_id")
    private Long recipeId;
    @Column(name = "food_id")
    private Long foodId;
    @Column(name = "total_score", precision = 12, scale = 6)
    private BigDecimal totalScore;
    @Column(length = 500)
    private String explanation;
    @Enumerated(EnumType.STRING) @Column(name = "user_decision", nullable = false, length = 20)
    private Decision userDecision;
    @Column(name = "decided_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime decidedAt;
    @Column(name = "created_at", insertable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected RecommendationResult() { }

    public RecommendationResult(RecommendationRequest request, short rankPosition,
            Long recipeId, Long foodId, BigDecimal totalScore) {
        this(request, rankPosition, recipeId, foodId, totalScore, null);
    }

    public RecommendationResult(RecommendationRequest request, short rankPosition,
            Long recipeId, Long foodId, BigDecimal totalScore, String explanation) {
        if (request == null || rankPosition < 1 || (recipeId == null) == (foodId == null)) {
            throw new IllegalArgumentException("Invalid recommendation result");
        }
        if (explanation != null && explanation.length() > 500) {
            throw new IllegalArgumentException("Invalid explanation");
        }
        this.request = request;
        this.rankPosition = rankPosition;
        this.recipeId = recipeId;
        this.foodId = foodId;
        this.totalScore = totalScore;
        this.explanation = explanation;
        this.userDecision = Decision.PENDING;
    }

    public Long id() { return id; }
    public RecommendationRequest request() { return request; }
    public Short rankPosition() { return rankPosition; }
    public Long recipeId() { return recipeId; }
    public Long foodId() { return foodId; }
    public BigDecimal totalScore() { return totalScore; }
    public String explanation() { return explanation; }
    public Decision userDecision() { return userDecision; }
    public LocalDateTime decidedAt() { return decidedAt; }
    public LocalDateTime createdAt() { return createdAt; }
}
