package com.smartmealplanner.food;

import java.util.List;
import java.util.UUID;

/** Only explicit catalog evidence is present; an absent row remains unknown. */
public record IngredientSafetySnapshot(UUID ingredientPublicId,
        List<AllergenEvidence> allergenFacts) {
    public IngredientSafetySnapshot {
        allergenFacts = List.copyOf(allergenFacts);
    }

    public record AllergenEvidence(String allergenCode,
            IngredientAllergenPresence presence) { }
}
