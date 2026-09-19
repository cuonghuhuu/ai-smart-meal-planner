package com.smartmealplanner.recipe;

import java.util.List;

/** Deterministic counts from one committed Recipe import document. */
public record RecipeImportReport(
        int recipesRead,
        int recipesCreated,
        int recipesUpdated,
        int recipesUnchanged,
        int ingredientLinesWritten,
        int stepsWritten,
        int nutritionSnapshotsComputed,
        List<RecipeImportIssue> warnings) {

    public RecipeImportReport {
        if (recipesRead < 0 || recipesCreated < 0 || recipesUpdated < 0
                || recipesUnchanged < 0 || ingredientLinesWritten < 0
                || stepsWritten < 0 || nutritionSnapshotsComputed < 0) {
            throw new IllegalArgumentException("Import counts must not be negative");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
