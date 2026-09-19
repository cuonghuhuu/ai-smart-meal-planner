package com.smartmealplanner.recipe;

/** Safe source-row-oriented Recipe import issue. */
public record RecipeImportIssue(
        RecipeImportIssueType type,
        String sourceIdentifier,
        String field,
        String detail) {

    public RecipeImportIssue {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        sourceIdentifier = normalize(sourceIdentifier);
        field = normalize(field);
        detail = normalize(detail);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
