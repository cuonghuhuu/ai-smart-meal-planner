package com.smartmealplanner.recipe;

/** Checked conversion between domain integers and SMALLINT persistence values. */
final class RecipeSmallInt {

    private RecipeSmallInt() {
    }

    static Short toShort(Integer value, String field) {
        if (value == null) {
            return null;
        }
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException(
                    field + " is outside the SMALLINT range");
        }
        return value.shortValue();
    }

    static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
