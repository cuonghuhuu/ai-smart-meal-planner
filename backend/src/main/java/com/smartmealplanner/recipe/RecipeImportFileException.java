package com.smartmealplanner.recipe;

/** Raised when a normalized Recipe import file cannot be decoded. */
public final class RecipeImportFileException extends RuntimeException {
    public RecipeImportFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
