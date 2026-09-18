package com.smartmealplanner.food;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Raised when a SMILING workbook cannot be converted into a safe import document. */
public final class SmilingVietnamWorkbookParseException extends RuntimeException {
    private final List<CatalogImportIssue> issues;
    private final List<CatalogImportIssue> warnings;
    private final int rowsParsed;
    private final int rowsSkipped;

    public SmilingVietnamWorkbookParseException(
            List<CatalogImportIssue> issues,
            List<CatalogImportIssue> warnings,
            int rowsParsed,
            int rowsSkipped) {
        super("SMILING Vietnam workbook parsing failed with "
                + (issues == null ? 0 : issues.size()) + " error(s)");
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("At least one issue is required");
        }
        if (rowsParsed < 0 || rowsSkipped < 0) {
            throw new IllegalArgumentException("Row counts must not be negative");
        }
        this.issues = List.copyOf(issues);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.rowsParsed = rowsParsed;
        this.rowsSkipped = rowsSkipped;
    }

    public SmilingVietnamWorkbookParseException(
            List<CatalogImportIssue> issues,
            int rowsParsed,
            int rowsSkipped) {
        this(issues, List.of(), rowsParsed, rowsSkipped);
    }

    public List<CatalogImportIssue> issues() {
        return issues;
    }

    public List<CatalogImportIssue> warnings() {
        return warnings;
    }

    public int rowsParsed() {
        return rowsParsed;
    }

    public int rowsSkipped() {
        return rowsSkipped;
    }

    public Map<CatalogImportIssueType, Long> issueCounts() {
        return countsByType(issues);
    }

    public Map<CatalogImportIssueType, Long> warningCounts() {
        return countsByType(warnings);
    }

    private static Map<CatalogImportIssueType, Long> countsByType(
            List<CatalogImportIssue> values) {
        Map<CatalogImportIssueType, Long> counts =
                new EnumMap<>(CatalogImportIssueType.class);
        for (CatalogImportIssue value : values) {
            counts.merge(value.type(), 1L, Long::sum);
        }
        return Collections.unmodifiableMap(counts);
    }
}
