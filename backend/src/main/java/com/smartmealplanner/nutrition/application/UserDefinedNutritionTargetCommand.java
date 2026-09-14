package com.smartmealplanner.nutrition.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * User-defined target command. Ownership and provenance are supplied by the
 * application workflow, never by this command.
 */
public record UserDefinedNutritionTargetCommand(
        LocalDate effectiveFrom,
        List<UserDefinedNutritionValueCommand> nutrientValues) {

    public UserDefinedNutritionTargetCommand {
        if (effectiveFrom == null) {
            throw NutritionApplicationException.invalidRequest(
                    "effectiveFrom is required");
        }

        if (nutrientValues == null || nutrientValues.isEmpty()) {
            throw NutritionApplicationException.invalidRequest(
                    "at least one nutrient value is required");
        }

        List<UserDefinedNutritionValueCommand> copiedValues =
                new ArrayList<>(nutrientValues.size());

        for (UserDefinedNutritionValueCommand value : nutrientValues) {
            if (value == null) {
                throw NutritionApplicationException.invalidRequest(
                        "nutrient value is required");
            }

            copiedValues.add(value);
        }

        nutrientValues = List.copyOf(copiedValues);
    }
}
