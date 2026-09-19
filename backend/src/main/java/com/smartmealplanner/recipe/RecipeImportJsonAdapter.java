package com.smartmealplanner.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Offline adapter for the typed, project-owned Recipe import JSON contract. */
public final class RecipeImportJsonAdapter {
    private final ObjectMapper objectMapper;

    public RecipeImportJsonAdapter(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper is required");
        }
        this.objectMapper = objectMapper;
    }

    public RecipeImportDocument read(Path sourceFile) {
        if (sourceFile == null) {
            throw new IllegalArgumentException("sourceFile is required");
        }
        try (InputStream input = Files.newInputStream(sourceFile)) {
            return read(input);
        } catch (IOException exception) {
            throw new RecipeImportFileException(
                    "Unable to read Recipe import file", exception);
        }
    }

    public RecipeImportDocument read(InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        try {
            RecipeImportDocument document = objectMapper.readValue(
                    input,
                    RecipeImportDocument.class);
            if (document == null) {
                throw new IllegalArgumentException("Recipe import document is null");
            }
            return document;
        } catch (IOException | RuntimeException exception) {
            throw new RecipeImportFileException(
                    "Unable to decode Recipe import document", exception);
        }
    }
}
