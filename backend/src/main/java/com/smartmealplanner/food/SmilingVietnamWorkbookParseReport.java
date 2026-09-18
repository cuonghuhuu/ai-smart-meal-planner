package com.smartmealplanner.food;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Successful parse result and row-level accounting for the SMILING workbook. */
public record SmilingVietnamWorkbookParseReport(
        CatalogImportDocument document,
        int rowsParsed,
        int rowsSkipped,
        List<CatalogImportIssue> warnings,
        Set<String> foodGroups,
        Set<String> foodSubgroups,
        Map<String, Integer> foodsByCategory,
        Map<String, Integer> foodsWithNutrient,
        Set<String> unmappedGroups,
        Set<String> unmappedSubgroups) {

    public SmilingVietnamWorkbookParseReport {
        if (document == null) {
            throw new IllegalArgumentException("document is required");
        }
        if (rowsParsed < 0 || rowsSkipped < 0) {
            throw new IllegalArgumentException("Row counts must not be negative");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        foodGroups = immutableSet(foodGroups);
        foodSubgroups = immutableSet(foodSubgroups);
        foodsByCategory = immutableCounts(foodsByCategory);
        foodsWithNutrient = immutableCounts(foodsWithNutrient);
        unmappedGroups = immutableSet(unmappedGroups);
        unmappedSubgroups = immutableSet(unmappedSubgroups);
    }

    public int foodsImported() {
        return document.foods().size();
    }

    public int distinctFoodGroupCount() {
        return foodGroups.size();
    }

    public int distinctFoodSubgroupCount() {
        return foodSubgroups.size();
    }

    public Map<CatalogImportIssueType, Long> warningCounts() {
        Map<CatalogImportIssueType, Long> counts =
                new EnumMap<>(CatalogImportIssueType.class);
        for (CatalogImportIssue warning : warnings) {
            counts.merge(warning.type(), 1L, Long::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    private static Set<String> immutableSet(Set<String> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static Map<String, Integer> immutableCounts(Map<String, Integer> values) {
        return values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
