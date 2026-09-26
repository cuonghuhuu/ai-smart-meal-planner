package com.smartmealplanner.recipe;

import java.util.List;

/** Raised before a Recipe import can persist any row. */
public final class RecipeImportValidationException extends RuntimeException {
    private final List<RecipeImportIssue> issues;

    public RecipeImportValidationException(List<RecipeImportIssue> issues) {
        super("Recipe import validation failed with "
                + (issues == null ? 0 : issues.size()) + " blocking issue(s)");
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("At least one issue is required");
        }
        this.issues = List.copyOf(issues);
    }

    public List<RecipeImportIssue> issues() {
        return issues;
    }
}
