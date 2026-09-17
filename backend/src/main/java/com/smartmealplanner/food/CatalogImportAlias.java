package com.smartmealplanner.food;

/** Explicit locale-aware alias supplied by catalog curation. */
public record CatalogImportAlias(String alias, String locale) {

    public CatalogImportAlias {
        if (alias == null || alias.isBlank() || alias.length() > 150) {
            throw new IllegalArgumentException("Invalid alias");
        }
        if (locale != null && locale.length() > 20) {
            throw new IllegalArgumentException("Invalid locale");
        }
        alias = alias.trim();
        locale = locale == null || locale.isBlank() ? null : locale.trim();
    }
}
