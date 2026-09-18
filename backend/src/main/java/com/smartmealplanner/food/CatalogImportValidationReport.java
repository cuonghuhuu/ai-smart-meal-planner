package com.smartmealplanner.food;

import java.util.List;

/** Preflight result; errors block persistence while warnings remain reportable. */
public record CatalogImportValidationReport(
        List<CatalogImportIssue> errors,
        List<CatalogImportIssue> warnings) {

    public CatalogImportValidationReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
