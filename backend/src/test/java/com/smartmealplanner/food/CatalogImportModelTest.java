package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogImportModelTest {
    @Test
    void rejectsNegativeAndUnrepresentableAmountsAtTheNormalizedBoundary() {
        assertThatThrownBy(() -> new CatalogImportNutrientFact(
                "protein",
                "PROTEIN",
                new BigDecimal("-0.1000"),
                "g",
                FoodNutrientDataQuality.ANALYTICAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid nutrient amount");

        assertThatThrownBy(() -> new CatalogImportNutrientFact(
                "protein",
                "PROTEIN",
                new BigDecimal("1.23456"),
                "g",
                FoodNutrientDataQuality.ANALYTICAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid nutrient amount");
    }

    @Test
    void missingNutritionIsAnEmptyFactSetRatherThanAZeroFact() {
        CatalogImportFood food = new CatalogImportFood(
                "SYNTHETIC-MISSING",
                "TEST_SMILING_VN_MISSING",
                "Synthetic missing nutrient food",
                null,
                "OTHER",
                null,
                NutritionBasis.PER_100_G,
                null,
                FoodSource.IMPORTED,
                "TEST_FIXTURE_ONLY#row=missing",
                List.of(),
                null);

        assertThat(food.nutrientFacts()).isEmpty();
    }
}
