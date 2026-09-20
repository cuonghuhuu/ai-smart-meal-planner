package com.smartmealplanner.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
@AutoConfigureMockMvc
@Testcontainers
class RecipeImportIT {

    private static final String SOURCE_PREFIX = "P9C_TEST:";
    private static final String RECIPE_SLUG = "p9c-test-recipe";
    private static final String INGREDIENT_CODE = "P9C_TEST_INGREDIENT";
    private static final String FOOD_CODE = "P9C_TEST_FOOD";
    private static final String REAL_SOURCE_PREFIX =
            "AI_MEAL_PLANNER_VN_CURATED_V1:";
    private static final String REAL_FOOD_SOURCE_REFERENCE =
            "P9C committed dataset synthetic catalog fixture";
    private static final List<String> REAL_INGREDIENT_CODES = List.of(
            "ING_SMILING_VN_1001",
            "ING_SMILING_VN_1004",
            "ING_SMILING_VN_1020",
            "ING_SMILING_VN_2008",
            "ING_SMILING_VN_3025",
            "ING_SMILING_VN_4002",
            "ING_SMILING_VN_4005",
            "ING_SMILING_VN_4010",
            "ING_SMILING_VN_4083",
            "ING_SMILING_VN_4103",
            "ING_SMILING_VN_5017",
            "ING_SMILING_VN_6002",
            "ING_SMILING_VN_7013",
            "ING_SMILING_VN_7017",
            "ING_SMILING_VN_8003",
            "ING_SMILING_VN_9001");

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

    @Autowired
    MockMvc mvc;

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

        String realRecipePattern = REAL_SOURCE_PREFIX + "%";
        jdbc.update("""
                delete from recipe_nutrition_values
                where snapshot_id in (
                    select id from recipe_nutrition_snapshots
                    where recipe_id in (
                        select id from recipes where source_reference like ?))
                """, realRecipePattern);
        jdbc.update("""
                delete from recipe_nutrition_snapshots
                where recipe_id in (
                    select id from recipes where source_reference like ?)
                """, realRecipePattern);
        jdbc.update("""
                delete from recipe_tag_assignments
                where recipe_id in (
                    select id from recipes where source_reference like ?)
                """, realRecipePattern);
        jdbc.update("""
                delete from recipe_meal_slot_types
                where recipe_id in (
                    select id from recipes where source_reference like ?)
                """, realRecipePattern);
        jdbc.update("""
                delete from recipe_steps
                where recipe_id in (
                    select id from recipes where source_reference like ?)
                """, realRecipePattern);
        jdbc.update("""
                delete from recipe_ingredients
                where recipe_id in (
                    select id from recipes where source_reference like ?)
                """, realRecipePattern);
        jdbc.update("delete from recipes where source_reference like ?",
                realRecipePattern);
        jdbc.update("""
                delete from ingredient_foods
                where ingredient_id in (
                    select id from ingredients where code like 'ING_SMILING_VN_%')
                """);
        jdbc.update("delete from ingredients where code like 'ING_SMILING_VN_%'");
        jdbc.update("""
                delete food_nutrient
                from food_nutrients food_nutrient
                join foods food on food.id = food_nutrient.food_id
                where food.source_reference = ?
                """, REAL_FOOD_SOURCE_REFERENCE);
        jdbc.update("delete from foods where source_reference = ?",
                REAL_FOOD_SOURCE_REFERENCE);
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

    @Test
    void importsCommittedVietnameseDatasetWithSyntheticCatalogFacts()
            throws Exception {
        insertRealCatalogFixture();
        RecipeImportDocument document = readCommittedDocument();

        assertThat(document.recipes()).hasSize(12);
        assertThat(document.recipes().stream()
                .map(RecipeImportRecipe::sourceIdentifier)
                .toList())
                .containsExactly(
                        "VN_CURATED_001",
                        "VN_CURATED_002",
                        "VN_CURATED_003",
                        "VN_CURATED_004",
                        "VN_CURATED_005",
                        "VN_CURATED_006",
                        "VN_CURATED_007",
                        "VN_CURATED_008",
                        "VN_CURATED_009",
                        "VN_CURATED_010",
                        "VN_CURATED_011",
                        "VN_CURATED_012");
        assertThat(document.recipes().stream()
                .flatMap(recipe -> recipe.ingredients().stream())
                .map(RecipeImportIngredient::ingredientCode)
                .distinct()
                .toList())
                .containsExactlyInAnyOrderElementsOf(REAL_INGREDIENT_CODES);
        assertThat(jdbc.queryForObject(
                "select count(*) from ingredients "
                        + "where code like 'ING_SMILING_VN_%'",
                Integer.class)).isEqualTo(REAL_INGREDIENT_CODES.size());

        int expectedIngredientLines = document.recipes().stream()
                .mapToInt(recipe -> recipe.ingredients().size())
                .sum();
        int expectedSteps = document.recipes().stream()
                .mapToInt(recipe -> recipe.steps().size())
                .sum();
        int expectedTags = document.recipes().stream()
                .mapToInt(recipe -> recipe.tags().size())
                .sum();
        int expectedMealSlots = document.recipes().stream()
                .mapToInt(recipe -> recipe.mealSlots().size())
                .sum();

        RecipeImportReport first = importer.importDocument(document);

        assertThat(first.recipesRead()).isEqualTo(12);
        assertThat(first.recipesCreated()).isEqualTo(12);
        assertThat(first.recipesUpdated()).isZero();
        assertThat(first.ingredientLinesWritten()).isEqualTo(expectedIngredientLines);
        assertThat(first.stepsWritten()).isEqualTo(expectedSteps);
        assertThat(first.nutritionSnapshotsComputed()).isEqualTo(12);
        assertThat(countRecipesBySource()).isEqualTo(12);
        assertThat(countChildren("recipe_ingredients")).isEqualTo(expectedIngredientLines);
        assertThat(countChildren("recipe_steps")).isEqualTo(expectedSteps);
        assertThat(countChildren("recipe_tag_assignments")).isEqualTo(expectedTags);
        assertThat(countChildren("recipe_meal_slot_types")).isEqualTo(expectedMealSlots);
        assertThat(countSnapshots()).isEqualTo(12);
        assertThat(countCurrentSnapshots()).isEqualTo(12);
        List<Timestamp> publishedAtValues = jdbc.query("""
                select published_at from recipes
                where source_reference like ?
                order by source_reference
                """, (result, row) -> result.getTimestamp(1),
                REAL_SOURCE_PREFIX + "%");
        assertThat(publishedAtValues).hasSize(12)
                .allSatisfy(value -> assertThat(value.toLocalDateTime().getNano() % 1_000)
                        .isZero());

        Map<String, UUID> publicIdsBefore = importedPublicIds();
        assertThat(publicIdsBefore.keySet())
                .containsExactlyInAnyOrderElementsOf(document.recipes().stream()
                        .map(RecipeImportRecipe::sourceReference)
                        .toList());
        for (RecipeImportRecipe recipe : document.recipes()) {
            Long recipeId = jdbc.queryForObject(
                    "select id from recipes where source_reference = ?",
                    Long.class,
                    recipe.sourceReference());
            List<Integer> lineNumbers = jdbc.query("""
                    select line_number from recipe_ingredients
                    where recipe_id = ? order by line_number
                    """, (result, row) -> result.getInt(1), recipeId);
            assertThat(lineNumbers).containsExactlyElementsOf(
                    recipe.ingredients().stream()
                            .map(RecipeImportIngredient::lineNumber)
                            .toList());
            List<String> persistedIngredients = jdbc.query("""
                    select ingredient.code
                    from recipe_ingredients line
                    join ingredients ingredient on ingredient.id = line.ingredient_id
                    where line.recipe_id = ?
                    order by line.line_number
                    """, (result, row) -> result.getString(1), recipeId);
            assertThat(persistedIngredients).containsExactlyElementsOf(
                    recipe.ingredients().stream()
                            .map(RecipeImportIngredient::ingredientCode)
                            .toList());
            List<Integer> stepNumbers = jdbc.query("""
                    select step_number from recipe_steps
                    where recipe_id = ? order by step_number
                    """, (result, row) -> result.getInt(1), recipeId);
            assertThat(stepNumbers).containsExactlyElementsOf(
                    recipe.steps().stream()
                            .map(RecipeImportStep::stepNumber)
                            .toList());
            List<String> persistedTags = jdbc.query("""
                    select tag.code
                    from recipe_tag_assignments assignment
                    join recipe_tags tag on tag.id = assignment.tag_id
                    where assignment.recipe_id = ?
                    """, (result, row) -> result.getString(1), recipeId);
            assertThat(persistedTags)
                    .containsExactlyInAnyOrderElementsOf(recipe.tags());
            List<String> persistedMealSlots = jdbc.query("""
                    select slot.code
                    from recipe_meal_slot_types assignment
                    join meal_slot_types slot
                      on slot.id = assignment.meal_slot_type_id
                    where assignment.recipe_id = ?
                    """, (result, row) -> result.getString(1), recipeId);
            assertThat(persistedMealSlots)
                    .containsExactlyInAnyOrderElementsOf(recipe.mealSlots());
        }

        UUID firstRecipeId = publicIdsBefore.values().iterator().next();
        mvc.perform(get("/api/v1/recipes/{publicId}", firstRecipeId)
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition").isNotEmpty());
        mvc.perform(get("/api/v1/recipes")
                .param("page", "0")
                .param("size", "100")
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12));

        RecipeImportReport second = importer.importDocument(document);

        assertThat(second.recipesRead()).isEqualTo(12);
        assertThat(second.recipesCreated()).isZero();
        assertThat(second.recipesUpdated()).isZero();
        assertThat(second.recipesUnchanged()).isEqualTo(12);
        assertThat(second.nutritionSnapshotsComputed()).isZero();
        assertThat(countRecipesBySource()).isEqualTo(12);
        assertThat(countChildren("recipe_ingredients")).isEqualTo(expectedIngredientLines);
        assertThat(countChildren("recipe_steps")).isEqualTo(expectedSteps);
        assertThat(countChildren("recipe_tag_assignments")).isEqualTo(expectedTags);
        assertThat(countChildren("recipe_meal_slot_types")).isEqualTo(expectedMealSlots);
        assertThat(countSnapshots()).isEqualTo(12);
        assertThat(countCurrentSnapshots()).isEqualTo(12);
        assertThat(importedPublicIds()).isEqualTo(publicIdsBefore);
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

    private void insertRealCatalogFixture() {
        Long unitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);
        Long nutrientId = jdbc.queryForObject(
                "select id from nutrients where code = 'ENERGY'", Long.class);

        for (String ingredientCode : REAL_INGREDIENT_CODES) {
            String sourceCode = ingredientCode.substring("ING_SMILING_VN_".length());
            UUID foodPublicId = UUID.randomUUID();
            String foodCode = "SMILING_VN_" + sourceCode;
            jdbc.update("""
                    insert into foods
                        (public_id, code, display_name, nutrition_basis, source,
                         source_reference, revision, is_active, version)
                    values (unhex(replace(?, '-', '')), ?, ?, 'PER_100_G',
                            'IMPORTED', ?, 1, true, 0)
                    """, foodPublicId.toString(), foodCode,
                    "P9C fixture food " + sourceCode,
                    REAL_FOOD_SOURCE_REFERENCE);
            Long foodId = jdbc.queryForObject(
                    "select id from foods where code = ?", Long.class, foodCode);
            jdbc.update("""
                    insert into food_nutrients (food_id, nutrient_id, amount, data_quality)
                    values (?, ?, 100.0000, 'ANALYTICAL')
                    """, foodId, nutrientId);

            UUID ingredientPublicId = UUID.randomUUID();
            jdbc.update("""
                    insert into ingredients
                        (public_id, code, display_name, default_food_id,
                         default_unit_id, is_staple, is_active, version)
                    values (unhex(replace(?, '-', '')), ?, ?, ?, ?, false, true, 0)
                    """, ingredientPublicId.toString(), ingredientCode,
                    "P9C fixture ingredient " + sourceCode, foodId, unitId);
            Long ingredientId = jdbc.queryForObject(
                    "select id from ingredients where code = ?", Long.class,
                    ingredientCode);
            jdbc.update("""
                    insert into ingredient_foods
                        (ingredient_id, food_id, preparation_state, yield_factor, is_primary)
                    values (?, ?, 'RAW', 1.0000, true)
                    """, ingredientId, foodId);
        }
    }

    private RecipeImportDocument readCommittedDocument() {
        InputStream resource = getClass().getResourceAsStream(
                "/recipe-import/vietnam-curated-v1.json");
        if (resource == null) {
            throw new AssertionError("Committed Vietnamese Recipe dataset is missing");
        }
        try (InputStream input = resource) {
            return new RecipeImportJsonAdapter(new ObjectMapper()).read(input);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read committed Recipe dataset", exception);
        }
    }

    private int countRecipesBySource() {
        return jdbc.queryForObject(
                "select count(*) from recipes where source_reference like ?",
                Integer.class,
                REAL_SOURCE_PREFIX + "%");
    }

    private int countChildren(String table) {
        return jdbc.queryForObject(
                "select count(*) from " + table
                        + " child join recipes recipe on recipe.id = child.recipe_id "
                        + "where recipe.source_reference like ?",
                Integer.class,
                REAL_SOURCE_PREFIX + "%");
    }

    private int countSnapshots() {
        return jdbc.queryForObject("""
                select count(*) from recipe_nutrition_snapshots snapshot
                join recipes recipe on recipe.id = snapshot.recipe_id
                where recipe.source_reference like ?
                """, Integer.class, REAL_SOURCE_PREFIX + "%");
    }

    private int countCurrentSnapshots() {
        return jdbc.queryForObject("""
                select count(*) from recipe_nutrition_snapshots snapshot
                join recipes recipe on recipe.id = snapshot.recipe_id
                where recipe.source_reference like ? and snapshot.is_current = true
                """, Integer.class, REAL_SOURCE_PREFIX + "%");
    }

    private Map<String, UUID> importedPublicIds() {
        Map<String, UUID> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select source_reference, public_id
                from recipes
                where source_reference like ?
                order by source_reference
                """, REAL_SOURCE_PREFIX + "%");
        for (Map<String, Object> row : rows) {
            result.put(
                    (String) row.get("source_reference"),
                    RecipeIds.bytesToUuid((byte[]) row.get("public_id")));
        }
        return result;
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
