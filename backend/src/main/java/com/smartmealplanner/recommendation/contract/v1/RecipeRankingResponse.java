package com.smartmealplanner.recommendation.contract.v1;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.AlgorithmVersion;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.InfeasibleReason;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.RankingStatus;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractCodes.ScoreComponentCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractLimits.*;

/** Strict internal response returned by the Python Recipe ranker. */
public record RecipeRankingResponse(
        @NotNull @Pattern(regexp = "^1$") String contractVersion,
        @NotNull UUID requestId,
        @NotNull AlgorithmVersion algorithmVersion,
        @NotNull RankingStatus status,
        @NotNull @Size(max = MAX_RESULT_LIMIT)
        List<@NotNull @Valid RankedRecipe> rankedRecipes,
        InfeasibleReason infeasibleReason) {

    public RecipeRankingResponse {
        rankedRecipes = rankedRecipes == null ? null : List.copyOf(rankedRecipes);
    }

    @JsonIgnore
    @AssertTrue(message = "response status and rankedRecipes are inconsistent")
    public boolean isStatusValid() {
        if (status == null || rankedRecipes == null) {
            return true;
        }
        return switch (status) {
            case SUCCEEDED -> !rankedRecipes.isEmpty() && infeasibleReason == null;
            case INFEASIBLE -> rankedRecipes.isEmpty() && infeasibleReason != null;
        };
    }

    @JsonIgnore
    @AssertTrue(message = "ranks and recipe public IDs must be contiguous and unique")
    public boolean isRankingValid() {
        if (rankedRecipes == null) {
            return true;
        }
        Set<UUID> ids = new HashSet<>();
        for (int index = 0; index < rankedRecipes.size(); index++) {
            RankedRecipe recipe = rankedRecipes.get(index);
            if (recipe != null && (recipe.rank() != index + 1
                    || recipe.recipePublicId() == null
                    || !ids.add(recipe.recipePublicId()))) {
                return false;
            }
        }
        return true;
    }

    public record RankedRecipe(
            @Min(1) @Max(MAX_RESULT_LIMIT) int rank,
            @NotNull UUID recipePublicId,
            @NotNull @DecimalMin("-0.20") @DecimalMax("1.00")
            @Digits(integer = 1, fraction = 6) BigDecimal totalScore,
            @NotNull @Size(min = MAX_SCORE_COMPONENTS, max = MAX_SCORE_COMPONENTS)
            List<@NotNull @Valid ScoreComponent> scoreComponents,
            @NotBlank @Size(max = MAX_EXPLANATION_LENGTH) String explanation) {

        public RankedRecipe {
            scoreComponents = scoreComponents == null ? null : List.copyOf(scoreComponents);
        }

        @JsonIgnore
        @AssertTrue(message = "scoreComponents must contain every component exactly once")
        public boolean isComponentSetValid() {
            if (scoreComponents == null) {
                return true;
            }
            Set<ScoreComponentCode> codes = new HashSet<>();
            for (ScoreComponent score : scoreComponents) {
                if (score != null && (score.componentCode() == null
                        || !codes.add(score.componentCode())
                        || score.weight() == null
                        || score.weight().compareTo(
                        score.componentCode().expectedPositiveWeight()) != 0)) {
                    return false;
                }
            }
            return codes.size() == ScoreComponentCode.values().length;
        }
    }

    public record ScoreComponent(
            @NotNull ScoreComponentCode componentCode,
            @NotNull @DecimalMin("0") @DecimalMax("1")
            @Digits(integer = 1, fraction = 6) BigDecimal value,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
            @Digits(integer = 1, fraction = 4) BigDecimal weight) {
    }
}
