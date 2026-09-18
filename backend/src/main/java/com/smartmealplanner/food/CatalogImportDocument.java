package com.smartmealplanner.food;

import java.util.List;

/** Offline normalized catalog document supplied by a trusted import operator. */
public record CatalogImportDocument(
        String dataset,
        String datasetVersion,
        List<CatalogImportFood> foods) {

    public CatalogImportDocument {
        dataset = requireText(dataset, 200, "dataset");
        datasetVersion = requireText(datasetVersion, 60, "datasetVersion");
        if (foods == null || foods.isEmpty()) {
            throw new IllegalArgumentException("At least one food is required");
        }
        foods = List.copyOf(foods);
    }

    private static String requireText(String value, int maximumLength, String field) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value.trim();
    }
}
