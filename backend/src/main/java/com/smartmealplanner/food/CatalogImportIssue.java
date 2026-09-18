package com.smartmealplanner.food;

/** Safe, source-row-oriented detail for one import validation outcome. */
public record CatalogImportIssue(
        CatalogImportIssueType type,
        String foodCode,
        String field,
        String detail) {

    public CatalogImportIssue {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        foodCode = foodCode == null || foodCode.isBlank() ? null : foodCode.trim();
        field = field == null || field.isBlank() ? null : field.trim();
        detail = detail == null || detail.isBlank() ? null : detail.trim();
    }
}
