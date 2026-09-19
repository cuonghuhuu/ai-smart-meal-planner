package com.smartmealplanner.recipe;

import java.util.List;

/** Project-owned normalized Recipe import document. */
public record RecipeImportDocument(
        String dataset,
        String datasetVersion,
        List<RecipeImportRecipe> recipes) {

    public RecipeImportDocument {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
    }
}
