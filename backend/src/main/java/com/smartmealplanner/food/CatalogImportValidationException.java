package com.smartmealplanner.food;

import java.util.List;

/** Raised before an invalid import can commit any catalog rows. */
public final class CatalogImportValidationException extends RuntimeException {
    private final List<CatalogImportIssue> issues;

    public CatalogImportValidationException(List<CatalogImportIssue> issues) {
        super("Catalog import validation failed with "
                + (issues == null ? 0 : issues.size()) + " blocking issue(s)");
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("At least one issue is required");
        }
        this.issues = List.copyOf(issues);
    }

    public List<CatalogImportIssue> issues() {
        return issues;
    }
}
