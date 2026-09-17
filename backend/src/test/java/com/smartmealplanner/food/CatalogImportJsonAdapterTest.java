package com.smartmealplanner.food;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogImportJsonAdapterTest {
    @Test
    void readsTheProjectOwnedNormalizedFixtureWithoutInventingMissingFacts() {
        InputStream input = getClass().getResourceAsStream(
                "/catalog-import/synthetic-vietnam.json");

        CatalogImportDocument document = new CatalogImportJsonAdapter(new ObjectMapper())
                .read(input);
        CatalogImportFood food = document.foods().getFirst();

        assertThat(document.dataset()).contains("Synthetic fixture only");
        assertThat(food.sourceIdentifier()).isEqualTo("SYNTHETIC-001");
        assertThat(food.catalogCode()).isEqualTo("TEST_SMILING_VN_001");
        assertThat(food.displayName()).isEqualTo("Synthetic rau muong");
        assertThat(food.categoryCode()).isEqualTo("VEG_LEAFY");
        assertThat(food.source()).isEqualTo(FoodSource.IMPORTED);
        assertThat(food.sourceReference())
                .isEqualTo("TEST_FIXTURE_ONLY#sheet=synthetic#row=1");
        assertThat(food.nutrientFacts())
                .extracting(CatalogImportNutrientFact::canonicalCode)
                .containsExactly("ENERGY", "PROTEIN", (String) null);
        assertThat(food.ingredientMapping().ingredientCode())
                .isEqualTo("test_synthetic_rau_muong");
    }
}
