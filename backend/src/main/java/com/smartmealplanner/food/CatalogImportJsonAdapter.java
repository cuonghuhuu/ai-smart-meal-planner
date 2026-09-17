package com.smartmealplanner.food;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Offline adapter for the project-owned normalized JSON interchange format.
 * It never downloads or interprets an upstream workbook.
 */
public final class CatalogImportJsonAdapter {
    private final ObjectMapper objectMapper;

    public CatalogImportJsonAdapter(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper is required");
        }
        this.objectMapper = objectMapper;
    }

    public CatalogImportDocument read(Path sourceFile) {
        if (sourceFile == null) {
            throw new IllegalArgumentException("sourceFile is required");
        }
        try (InputStream input = Files.newInputStream(sourceFile)) {
            return objectMapper.readValue(input, CatalogImportDocument.class);
        } catch (IOException exception) {
            throw new CatalogImportFileException(
                    "Unable to read normalized catalog import file", exception);
        }
    }

    public CatalogImportDocument read(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        try {
            return objectMapper.readValue(input, CatalogImportDocument.class);
        } catch (IOException exception) {
            throw new CatalogImportFileException(
                    "Unable to decode normalized catalog import document", exception);
        }
    }
}
