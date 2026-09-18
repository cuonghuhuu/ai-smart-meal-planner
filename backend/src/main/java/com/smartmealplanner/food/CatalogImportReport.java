package com.smartmealplanner.food;

import java.util.List;

/** Counts and non-blocking warnings from one committed catalog import. */
public record CatalogImportReport(
        int foodsRead,
        int foodsCreated,
        int foodsUpdated,
        int ingredientsCreated,
        int ingredientsUpdated,
        List<CatalogImportIssue> warnings) {

    public CatalogImportReport {
        if (foodsRead < 0 || foodsCreated < 0 || foodsUpdated < 0
                || ingredientsCreated < 0 || ingredientsUpdated < 0) {
            throw new IllegalArgumentException("Import counts must not be negative");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
