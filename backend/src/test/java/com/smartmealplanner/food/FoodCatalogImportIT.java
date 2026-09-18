package com.smartmealplanner.food;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MySQL coverage for the offline, idempotent catalog import boundary. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class FoodCatalogImportIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p8_catalog_import")
            .withUsername("p8_catalog_import_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired FoodCatalogImportService importer;
    @Autowired ObjectMapper objectMapper;
    @Autowired FoodRepository foods;
    @Autowired IngredientRepository ingredients;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext applicationContext;

    @BeforeEach
    void removeSyntheticRows() {
        TransactionTemplate cleanup = new TransactionTemplate(transactions);
        cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> {
            jdbc.update("""
                    DELETE ingredient_alias
                    FROM ingredient_aliases ingredient_alias
                    JOIN ingredients ingredient
                      ON ingredient.id = ingredient_alias.ingredient_id
                    WHERE ingredient.code = ?
                    """, "test_synthetic_rau_muong");
            jdbc.update("""
                    DELETE ingredient_food
                    FROM ingredient_foods ingredient_food
                    LEFT JOIN ingredients ingredient
                      ON ingredient.id = ingredient_food.ingredient_id
                    LEFT JOIN foods food
                      ON food.id = ingredient_food.food_id
                    WHERE ingredient.code = ?
                       OR food.code IN (?, ?)
                    """, "test_synthetic_rau_muong",
                    "TEST_SMILING_VN_001", "TEST_SMILING_VN_002");
            jdbc.update(
                    "DELETE FROM ingredients WHERE code = ?",
                    "test_synthetic_rau_muong");
            jdbc.update("""
                    DELETE food_nutrient
                    FROM food_nutrients food_nutrient
                    JOIN foods food ON food.id = food_nutrient.food_id
                    WHERE food.code IN (?, ?)
                    """, "TEST_SMILING_VN_001", "TEST_SMILING_VN_002");
            jdbc.update("""
                    DELETE food_serving
                    FROM food_servings food_serving
                    JOIN foods food ON food.id = food_serving.food_id
                    WHERE food.code IN (?, ?)
                    """, "TEST_SMILING_VN_001", "TEST_SMILING_VN_002");
            jdbc.update(
                    "DELETE FROM foods WHERE code IN (?, ?)",
                    "TEST_SMILING_VN_001", "TEST_SMILING_VN_002");
        });

        assertThat(foods.findByCode("TEST_SMILING_VN_001")).isEmpty();
        assertThat(foods.findByCode("TEST_SMILING_VN_002")).isEmpty();
        assertThat(ingredients.findByCode("test_synthetic_rau_muong")).isEmpty();
    }

    @Test
    void firstImportCreatesFoodNutrientsAndOnlyExplicitIngredientMappings() {
        CatalogImportReport report = importer.importDocument(fixture());

        assertThat(report.foodsRead()).isEqualTo(1);
        assertThat(report.foodsCreated()).isEqualTo(1);
        assertThat(report.ingredientsCreated()).isEqualTo(1);
        assertThat(report.warnings())
                .singleElement()
                .extracting(CatalogImportIssue::type)
                .isEqualTo(CatalogImportIssueType.UNSUPPORTED_NUTRIENT);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Food food = foods.findByCode("TEST_SMILING_VN_001").orElseThrow();
            assertThat(food.source()).isEqualTo(FoodSource.IMPORTED);
            assertThat(food.sourceReference())
                    .isEqualTo("TEST_FIXTURE_ONLY#sheet=synthetic#row=1");
            assertThat(food.nutrientFacts())
                    .extracting(fact -> fact.nutrient().code())
                    .containsExactlyInAnyOrder("ENERGY", "PROTEIN");
            assertThat(food.nutrientFacts())
                    .filteredOn(fact -> fact.nutrient().code().equals("ENERGY"))
                    .singleElement()
                    .satisfies(fact -> {
                        assertThat(fact.amount()).isEqualByComparingTo("25.1000");
                        assertThat(fact.nutrient().unit().code()).isEqualTo("kcal");
                    });
            assertThat(food.nutrientFacts())
                    .filteredOn(fact -> fact.nutrient().code().equals("PROTEIN"))
                    .singleElement()
                    .satisfies(fact -> assertThat(fact.nutrient().unit().code()).isEqualTo("g"));

            Ingredient ingredient = ingredients.findByCode("test_synthetic_rau_muong")
                    .orElseThrow();
            assertThat(ingredient.defaultFood().code()).isEqualTo(food.code());
            assertThat(ingredient.foodMappings()).hasSize(1);
            assertThat(ingredient.aliases()).hasSize(2);
        });
    }

    @Test
    void repeatedImportDoesNotDuplicateRowsAndPreservesPublicIdentity() {
        importer.importDocument(fixture());
        UUID publicId = foods.findByCode("TEST_SMILING_VN_001")
                .orElseThrow()
                .publicId();
        UUID ingredientPublicId = ingredients.findByCode("test_synthetic_rau_muong")
                .orElseThrow()
                .publicId();
        int foodRevision = foods.findByCode("TEST_SMILING_VN_001")
                .orElseThrow()
                .revision();
        int foodCount = count("foods", "code", "TEST_SMILING_VN_001");
        int nutrientCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_nutrients fn "
                        + "JOIN foods f ON f.id = fn.food_id WHERE f.code = ?",
                Integer.class,
                "TEST_SMILING_VN_001");
        int aliasCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingredient_aliases ia "
                        + "JOIN ingredients i ON i.id = ia.ingredient_id WHERE i.code = ?",
                Integer.class,
                "test_synthetic_rau_muong");

        CatalogImportReport report = importer.importDocument(fixture());

        assertThat(report.foodsCreated()).isZero();
        assertThat(report.foodsUpdated()).isEqualTo(1);
        assertThat(report.ingredientsCreated()).isZero();
        assertThat(report.ingredientsUpdated()).isEqualTo(1);
        assertThat(count("foods", "code", "TEST_SMILING_VN_001")).isEqualTo(foodCount);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM food_nutrients fn "
                        + "JOIN foods f ON f.id = fn.food_id WHERE f.code = ?",
                Integer.class,
                "TEST_SMILING_VN_001")).isEqualTo(nutrientCount);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingredient_aliases ia "
                        + "JOIN ingredients i ON i.id = ia.ingredient_id WHERE i.code = ?",
                Integer.class,
                "test_synthetic_rau_muong")).isEqualTo(aliasCount);
        assertThat(foods.findByCode("TEST_SMILING_VN_001").orElseThrow().publicId())
                .isEqualTo(publicId);
        assertThat(ingredients.findByCode("test_synthetic_rau_muong").orElseThrow().publicId())
                .isEqualTo(ingredientPublicId);
        assertThat(foods.findByCode("TEST_SMILING_VN_001").orElseThrow().revision())
                .isEqualTo(foodRevision);
    }

    @Test
    void reimportUpdatesNutritionAndIncrementsFoodRevisionOnce() {
        importer.importDocument(fixture());
        CatalogImportFood original = fixture().foods().getFirst();
        List<CatalogImportNutrientFact> updatedFacts = original.nutrientFacts().stream()
                .map(fact -> fact.canonicalCode() != null
                        && fact.canonicalCode().equals("ENERGY")
                        ? new CatalogImportNutrientFact(
                                fact.sourceCode(),
                                fact.canonicalCode(),
                                new BigDecimal("26.2000"),
                                fact.unitCode(),
                                fact.dataQuality())
                        : fact)
                .toList();
        CatalogImportFood updatedFood = new CatalogImportFood(
                original.sourceIdentifier(),
                original.catalogCode(),
                original.displayName(),
                original.sourceName(),
                original.categoryCode(),
                original.description(),
                original.nutritionBasis(),
                original.densityGPerMl(),
                original.source(),
                original.sourceReference(),
                updatedFacts,
                original.ingredientMapping());

        importer.importDocument(new CatalogImportDocument(
                "Synthetic fixture only - SMILING Vietnam import boundary",
                "test",
                List.of(updatedFood)));

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Food food = foods.findByCode("TEST_SMILING_VN_001").orElseThrow();
            assertThat(food.revision()).isEqualTo(2);
            assertThat(food.nutrientFacts())
                    .filteredOn(fact -> fact.nutrient().code().equals("ENERGY"))
                    .singleElement()
                    .satisfies(fact -> assertThat(fact.amount()).isEqualByComparingTo("26.2000"));
        });
    }

    @Test
    void unknownCategoriesAndDuplicateNutrientsAreReportedAsBlockingIssues() {
        CatalogImportFood original = fixture().foods().getFirst();
        CatalogImportFood unknownCategory = copyFood(
                original,
                "SYNTHETIC-002",
                "TEST_SMILING_VN_002",
                "NO_SUCH_CATEGORY",
                original.nutrientFacts());
        CatalogImportFood duplicateNutrient = copyFood(
                original,
                "SYNTHETIC-002",
                "TEST_SMILING_VN_002",
                original.categoryCode(),
                new ArrayList<>(List.of(
                        original.nutrientFacts().getFirst(),
                        original.nutrientFacts().getFirst())));

        assertThatThrownBy(() -> importer.importDocument(new CatalogImportDocument(
                "Synthetic fixture only - SMILING Vietnam import boundary",
                "test",
                List.of(unknownCategory))))
                .isInstanceOfSatisfying(
                        CatalogImportValidationException.class,
                        exception -> assertThat(exception.issues())
                                .extracting(CatalogImportIssue::type)
                                .contains(CatalogImportIssueType.UNKNOWN_CATEGORY));

        assertThatThrownBy(() -> importer.importDocument(new CatalogImportDocument(
                "Synthetic fixture only - SMILING Vietnam import boundary",
                "test",
                List.of(duplicateNutrient))))
                .isInstanceOfSatisfying(
                        CatalogImportValidationException.class,
                        exception -> assertThat(exception.issues())
                                .extracting(CatalogImportIssue::type)
                                .contains(CatalogImportIssueType.DUPLICATE_NUTRIENT));
        assertThat(foods.findByCode("TEST_SMILING_VN_002")).isEmpty();
    }

    @Test
    void invalidSecondRecordRollsBackTheFirstRecord() {
        CatalogImportFood original = fixture().foods().getFirst();
        CatalogImportFood invalid = copyFood(
                original,
                "SYNTHETIC-002",
                "TEST_SMILING_VN_002",
                "NO_SUCH_CATEGORY",
                original.nutrientFacts());

        assertThatThrownBy(() -> importer.importDocument(new CatalogImportDocument(
                "Synthetic fixture only - SMILING Vietnam import boundary",
                "test",
                List.of(original, invalid))))
                .isInstanceOf(CatalogImportValidationException.class);

        assertThat(foods.findByCode("TEST_SMILING_VN_001")).isEmpty();
        assertThat(foods.findByCode("TEST_SMILING_VN_002")).isEmpty();
        assertThat(ingredients.findByCode("test_synthetic_rau_muong")).isEmpty();
    }

    @Test
    void normalApplicationStartupDoesNotRegisterTheImportRunner() {
        assertThat(applicationContext.containsBean("catalogImportRunner")).isFalse();
    }

    private CatalogImportDocument fixture() {
        InputStream input = getClass().getResourceAsStream(
                "/catalog-import/synthetic-vietnam.json");
        return new CatalogImportJsonAdapter(objectMapper).read(input);
    }

    private static CatalogImportFood copyFood(
            CatalogImportFood original,
            String sourceIdentifier,
            String code,
            String categoryCode,
            List<CatalogImportNutrientFact> facts) {
        return new CatalogImportFood(
                sourceIdentifier,
                code,
                original.displayName(),
                original.sourceName(),
                categoryCode,
                original.description(),
                original.nutritionBasis(),
                original.densityGPerMl(),
                original.source(),
                original.sourceReference() + "#" + code,
                facts,
                null);
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value);
    }
}
