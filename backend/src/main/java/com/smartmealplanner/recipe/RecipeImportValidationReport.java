package com.smartmealplanner.recipe;

import java.util.List;

/** Read-only preflight outcome; errors prevent all persistence. */
public record RecipeImportValidationReport(
        List<RecipeImportIssue> errors,
        List<RecipeImportIssue> warnings) {

    public RecipeImportValidationReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
