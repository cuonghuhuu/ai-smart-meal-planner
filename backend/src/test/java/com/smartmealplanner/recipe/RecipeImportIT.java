package com.smartmealplanner.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL coverage for the explicit, idempotent P9C Recipe import boundary. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RecipeImportIT {

    private static final String SOURCE_PREFIX = "P9C_TEST:";
    private static final String RECIPE_SLUG = "p9c-test-recipe";
    private static final String INGREDIENT_CODE = "P9C_TEST_INGREDIENT";
    private static final String FOOD_CODE = "P9C_TEST_FOOD";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p9_recipe_import")
            .withUsername("p9_recipe_import_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    RecipeImportService importer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanSyntheticRows() {
        jdbc.update("""
                delete from recipe_nutrition_values
                where snapshot_id in (
                    select id from recipe_nutrition_snapshots
                    where recipe_id in (select id from recipes where slug = ?))
                """, RECIPE_SLUG);
        jdbc.update("""
                delete from recipe_nutrition_snapshots
                where recipe_id in (select id from recipes where slug = ?)
                """, RECIPE_SLUG);
        jdbc.update("""
                delete from recipe_tag_assignments
                where recipe_id in (select id from recipes where slug = ?)
                """, RECIPE_SLUG);
        jdbc.update("""
                delete from recipe_meal_slot_types
                where recipe_id in (select id from recipes where slug = ?)
                """, RECIPE_SLUG);
        jdbc.update("""
                delete from recipe_steps
                where recipe_id in (select id from recipes where slug = ?)
                """, RECIPE_SLUG);
        jdbc.update("""
                delete from recipe_ingredients
                where recipe_id in (select id from recipes where slug = ?)
                """, RECIPE_SLUG);
        jdbc.update("delete from recipes where slug = ?", RECIPE_SLUG);
        jdbc.update("delete from ingredients where code = ?", INGREDIENT_CODE);
        jdbc.update("""
                delete food_nutrient
                from food_nutrients food_nutrient
                join foods food on food.id = food_nutrient.food_id
                where food.code = ?
                """, FOOD_CODE);
        jdbc.update("delete from foods where code = ?", FOOD_CODE);
    }

    @Test
    void createsPublishedRecipeAndP9BSnapshotThenRerunIsIdempotent() {
        insertCatalogFixture();

        RecipeImportReport first = importer.importDocument(document(100));
        UUID publicId = jdbc.queryForObject(
                "select public_id from recipes where slug = ?", (result, row) -> {
                    byte[] bytes = result.getBytes(1);
                    return RecipeIds.bytesToUuid(bytes);
                }, RECIPE_SLUG);

        assertThat(first.recipesCreated()).isEqualTo(1);
        assertThat(first.nutritionSnapshotsComputed()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from recipes where slug = ?", String.class, RECIPE_SLUG))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject(
                "select created_by_user_id from recipes where slug = ?",
                Long.class,
                RECIPE_SLUG)).isNull();
        assertThat(count("recipe_ingredients")).isEqualTo(1);
        assertThat(count("recipe_steps")).isEqualTo(1);
        assertThat(count("recipe_nutrition_snapshots")).isEqualTo(1);

        RecipeImportReport second = importer.importDocument(document(100));

        assertThat(second.recipesCreated()).isZero();
        assertThat(second.recipesUpdated()).isZero();
        assertThat(second.recipesUnchanged()).isEqualTo(1);
        assertThat(second.nutritionSnapshotsComputed()).isZero();
        UUID actualPublicId = jdbc.queryForObject(
                "select public_id from recipes where slug = ?",
                (result, row) -> RecipeIds.bytesToUuid(result.getBytes(1)),
                RECIPE_SLUG);
        assertThat(actualPublicId).isEqualTo(publicId);
        assertThat(count("recipe_ingredients")).isEqualTo(1);
        assertThat(count("recipe_steps")).isEqualTo(1);
        assertThat(count("recipe_nutrition_snapshots")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from recipe_nutrition_snapshots "
                        + "where recipe_id = (select id from recipes where slug = ?) "
                        + "and is_current = true",
                Integer.class,
                RECIPE_SLUG)).isEqualTo(1);
    }

    @Test
    void materialQuantityChangeReplacesChildrenAndCreatesHistoricalSnapshot() {
        insertCatalogFixture();
        importer.importDocument(document(100));

        RecipeImportReport update = importer.importDocument(document(200));

        assertThat(update.recipesUpdated()).isEqualTo(1);
        assertThat(update.nutritionSnapshotsComputed()).isEqualTo(1);
        assertThat(count("recipe_nutrition_snapshots")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from recipe_nutrition_snapshots "
                        + "where recipe_id = (select id from recipes where slug = ?) "
                        + "and is_current = true",
                Integer.class,
                RECIPE_SLUG)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select quantity from recipe_ingredients "
                        + "where recipe_id = (select id from recipes where slug = ?)",
                BigDecimal.class,
                RECIPE_SLUG)).isEqualByComparingTo("200");
    }

    @Test
    void unknownIngredientBlocksBeforeAnyRecipeIsPersisted() {
        RecipeImportDocument invalid = document(100, "P9C_UNKNOWN_INGREDIENT");

        assertThatThrownBy(() -> importer.importDocument(invalid))
                .isInstanceOf(RecipeImportValidationException.class)
                .satisfies(exception -> assertThat(
                        ((RecipeImportValidationException) exception).issues())
                        .extracting(RecipeImportIssue::type)
                        .contains(RecipeImportIssueType.UNKNOWN_INGREDIENT));
        assertThat(jdbc.queryForObject(
                "select count(*) from recipes where slug = ?",
                Integer.class,
                RECIPE_SLUG)).isZero();
    }

    private void insertCatalogFixture() {
        Long unitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);
        Long nutrientId = jdbc.queryForObject(
                "select id from nutrients where code = 'ENERGY'", Long.class);
        UUID foodPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, nutrition_basis, source,
                     source_reference, revision, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9C test food',
                        'PER_100_G', 'CURATED', ?, 1, true, 0)
                """, foodPublicId.toString(), FOOD_CODE, SOURCE_PREFIX + "FOOD");
        Long foodId = jdbc.queryForObject(
                "select id from foods where code = ?", Long.class, FOOD_CODE);
        jdbc.update("""
                insert into food_nutrients (food_id, nutrient_id, amount, data_quality)
                values (?, ?, 100.0000, 'CALCULATED')
                """, foodId, nutrientId);
        UUID ingredientPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, default_food_id,
                     default_unit_id, is_staple, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9C test ingredient',
                        ?, ?, false, true, 0)
                """, ingredientPublicId.toString(), INGREDIENT_CODE, foodId, unitId);
    }

    private RecipeImportDocument document(int quantity) {
        return document(quantity, INGREDIENT_CODE);
    }

    private RecipeImportDocument document(int quantity, String ingredientCode) {
        return new RecipeImportDocument(
                "Synthetic fixture only - P9C",
                "test-v1",
                List.of(new RecipeImportRecipe(
                        "P9C_TEST_001",
                        "Cơm thử nghiệm",
                        RECIPE_SLUG,
                        "Công thức tổng hợp cho kiểm thử nhập liệu.",
                        2,
                        5,
                        10,
                        RecipeDifficulty.EASY,
                        null,
                        null,
                        RecipeSource.CURATED,
                        SOURCE_PREFIX + "RECIPE_001",
                        List.of("VIETNAMESE", "MAIN_DISH"),
                        List.of("LUNCH"),
                        List.of(new RecipeImportIngredient(
                                1,
                                ingredientCode,
                                BigDecimal.valueOf(quantity),
                                "g",
                                null,
                                false,
                                true,
                                null)),
                        List.of(new RecipeImportStep(
                                1,
                                "Nấu nguyên liệu đến khi chín mềm.",
                                10)))));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
