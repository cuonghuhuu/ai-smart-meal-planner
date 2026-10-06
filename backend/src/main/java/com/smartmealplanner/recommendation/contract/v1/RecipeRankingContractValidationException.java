package com.smartmealplanner.recommendation.contract.v1;

import java.util.List;

/** Raised when Recipe Ranking JSON violates internal contract version 1. */
public final class RecipeRankingContractValidationException extends RuntimeException {
    private final List<String> violations;

    RecipeRankingContractValidationException(List<String> violations) {
        super("Recipe-ranking contract validation failed: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
