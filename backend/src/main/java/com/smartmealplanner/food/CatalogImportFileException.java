package com.smartmealplanner.food;

/** Raised when the local normalized import file cannot be read or decoded. */
public final class CatalogImportFileException extends RuntimeException {
    public CatalogImportFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
