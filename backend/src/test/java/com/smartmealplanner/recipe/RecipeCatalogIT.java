package com.smartmealplanner.recipe;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL coverage for published-only Recipe catalog reads. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class RecipeCatalogIT {

    private static final String SLUG_PREFIX = "p9-it-";
    private static final String FOOD_SOURCE_REFERENCE = "P9 recipe catalog test";
    private static final String INGREDIENT_CODE_PREFIX = "P9_IT_INGREDIENT_";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p9_recipe_catalog")
            .withUsername("p9_recipe_catalog_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

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
                    where recipe_id in (
                        select id from recipes where slug like ?))
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_nutrition_snapshots
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_tag_assignments
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_meal_slot_types
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_steps
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("""
                delete from recipe_ingredients
                where recipe_id in (select id from recipes where slug like ?)
                """, SLUG_PREFIX + "%");
        jdbc.update("delete from recipes where slug like ?", SLUG_PREFIX + "%");
        jdbc.update("""
                delete from ingredient_foods
                where ingredient_id in (
                    select id from ingredients where code like ?)
                """, INGREDIENT_CODE_PREFIX + "%");
        jdbc.update("delete from ingredients where code like ?",
                INGREDIENT_CODE_PREFIX + "%");
        jdbc.update("delete from foods where source_reference = ?",
                FOOD_SOURCE_REFERENCE);
    }

    @Test
    void requiresAuthenticationAndExposesPublishedRecipesOnly() throws Exception {
        UUID published = insertRecipe(
                "Published P9 Recipe", "published", 5, 10,
                "PUBLISHED", publishedAt(), null);
        UUID draft = insertRecipe(
                "Draft P9 Recipe", "draft", 5, 10,
                "DRAFT", null, null);
        UUID archived = insertRecipe(
                "Archived P9 Recipe", "archived", 5, 10,
                "ARCHIVED", publishedAt(), archivedAt());

        mvc.perform(get("/api/v1/recipes"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/recipes").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(org.hamcrest.Matchers.hasItem(published.toString())))
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(draft.toString()))))
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(archived.toString()))));

        mvc.perform(get("/api/v1/recipes/{publicId}", draft).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
        mvc.perform(get("/api/v1/recipes/{publicId}", published).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition")
                        .value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/recipes/{publicId}", archived).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_FOUND"));
        mvc.perform(get("/api/v1/recipes/{publicId}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recipes/not-a-uuid").with(jwt()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reference/recipe-tags").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist());
        mvc.perform(get("/api/v1/reference/meal-slot-types").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void appliesFullTextTagMealSlotWildcardMaxMinutesAndPagination() throws Exception {
        UUID unclassified = insertRecipe(
                "Vietnamese P9AlphaUniqueMarker Recipe Alpha", "wildcard", 2, 10,
                "PUBLISHED", publishedAt(), null);
        UUID breakfast = insertRecipe(
                "Vietnamese P9 Recipe Beta", "breakfast", 2, 10,
                "PUBLISHED", publishedAt().minusMinutes(1), null);
        UUID slow = insertRecipe(
                "Vietnamese P9 Recipe Gamma", "slow", 60, 70,
                "PUBLISHED", publishedAt().minusMinutes(2), null);
        assignMealSlot(breakfast, "BREAKFAST");
        assignTag(unclassified, "VEGAN");

        mvc.perform(get("/api/v1/recipes")
                        .param("q", "P9AlphaUniqueMarker")
                        .param("page", "0")
                        .param("size", "1")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId")
                        .value(unclassified.toString()))
                .andExpect(jsonPath("$.content[0].source").value("CURATED"))
                .andExpect(jsonPath("$.content[0].tags[0].code")
                        .value("VEGAN"))
                .andExpect(jsonPath("$.content[0].mealSlots")
                        .isEmpty());

        mvc.perform(get("/api/v1/recipes")
                        .param("mealSlotCode", "LUNCH")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(org.hamcrest.Matchers.hasItem(unclassified.toString())))
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(breakfast.toString()))));

        mvc.perform(get("/api/v1/recipes")
                        .param("tagCode", "VEGAN")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(unclassified.toString()));

        mvc.perform(get("/api/v1/recipes")
                        .param("maxMinutes", "20")
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(slow.toString()))));

        mvc.perform(get("/api/v1/recipes")
                        .param("mealSlotCode", "UNKNOWN_SLOT")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/recipes")
                        .param("tagCode", "UNKNOWN_TAG")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/recipes")
                        .param("maxMinutes", "10081")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/recipes")
                        .param("page", "-1")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/recipes")
                        .param("size", "101")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void readsOrderedDetailReferencesAndCurrentNutritionWithoutInventingValues()
            throws Exception {
        CatalogFixture catalog = insertCatalogFixture();
        UUID recipePublicId = insertRecipe(
                "P9 detail recipe", "detail", 2, 10,
                "PUBLISHED", publishedAt(), null);
        Long recipeId = recipeId(recipePublicId);

        jdbc.update("""
                insert into recipe_ingredients
                    (recipe_id, line_number, ingredient_id, food_id,
                     quantity, unit_id, preparation_note, is_optional,
                     allow_substitution, section_label)
                values (?, 2, ?, ?, 2.0000, ?, 'second', false, true, 'Main')
                """, recipeId, catalog.secondIngredientId, catalog.foodId, catalog.unitId);
        jdbc.update("""
                insert into recipe_ingredients
                    (recipe_id, line_number, ingredient_id, food_id,
                     quantity, unit_id, preparation_note, is_optional,
                     allow_substitution, section_label)
                values (?, 1, ?, null, null, null, null, true, false, 'Main')
                """, recipeId, catalog.ingredientId);
        jdbc.update("""
                insert into recipe_steps (recipe_id, step_number, instruction,
                    duration_minutes)
                values (?, 2, 'Second step', 2)
                """, recipeId);
        jdbc.update("""
                insert into recipe_steps (recipe_id, step_number, instruction,
                    duration_minutes)
                values (?, 1, 'First step', null)
                """, recipeId);
        assignTag(recipePublicId, "MAIN_DISH");
        assignMealSlot(recipePublicId, "DINNER");

        jdbc.update("""
                insert into recipe_nutrition_snapshots
                    (recipe_id, ingredient_revision, completeness_ratio,
                     computation_note, is_current)
                values (?, 1, 1.0000, 'P9 fixture', true)
                """, recipeId);
        Long snapshotId = jdbc.queryForObject("""
                select id from recipe_nutrition_snapshots where recipe_id = ?
                """, Long.class, recipeId);
        Long nutrientId = jdbc.queryForObject(
                "select id from nutrients where code = 'PROTEIN'", Long.class);
        jdbc.update("""
                insert into recipe_nutrition_values
                    (snapshot_id, nutrient_id, amount_per_serving)
                values (?, ?, 12.5000)
                """, snapshotId, nutrientId);

        mvc.perform(get("/api/v1/recipes/{publicId}", recipePublicId)
                .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients[0].lineNumber").value(1))
                .andExpect(jsonPath("$.ingredients[0].foodPublicId")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ingredients[0].foodCode")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ingredients[0].foodDisplayName")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ingredients[1].lineNumber").value(2))
                .andExpect(jsonPath("$.ingredients[1].foodPublicId")
                        .value(catalog.foodPublicId.toString()))
                .andExpect(jsonPath("$.ingredients[1].foodDisplayName")
                        .value("P9 Food"))
                .andExpect(jsonPath("$.ingredients[1].foodCode")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.steps[0].stepNumber").value(1))
                .andExpect(jsonPath("$.steps[1].stepNumber").value(2))
                .andExpect(jsonPath("$.nutrition.values[0].nutrientCode")
                        .value("PROTEIN"))
                .andExpect(jsonPath("$.nutrition.values[0].amountPerServing")
                        .value(12.5))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ingredients[0].ingredientId").doesNotExist())
                .andExpect(jsonPath("$.nutrition.values[0].nutrientId").doesNotExist());
    }

    private UUID insertRecipe(
            String title,
            String slugSuffix,
            int prepMinutes,
            int cookMinutes,
            String status,
            LocalDateTime publishedAt,
            LocalDateTime archivedAt) {

        UUID publicId = UUID.randomUUID();
        jdbc.update("""
                insert into recipes
                    (public_id, title, slug, summary, servings, prep_minutes,
                     cook_minutes, difficulty, instructions_note, image_url,
                     source, source_reference, status, published_at,
                     archived_at, version)
                values (unhex(replace(?, '-', '')), ?, ?, ?, 2, ?, ?,
                        'EASY', null, null, 'CURATED', 'P9 test', ?, ?, ?, 0)
                """, publicId.toString(), title,
                SLUG_PREFIX + slugSuffix + "-" + publicId,
                "P9 recipe fixture", prepMinutes, cookMinutes, status,
                timestamp(publishedAt), timestamp(archivedAt));
        return publicId;
    }

    private CatalogFixture insertCatalogFixture() {
        Long categoryId = jdbc.queryForObject(
                "select id from food_categories where code = 'PROTEIN'",
                Long.class);
        Long unitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'",
                Long.class);
        UUID foodPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, food_category_id,
                     nutrition_basis, source, source_reference, revision,
                     is_active, version)
                values (unhex(replace(?, '-', '')), null, 'P9 Food', ?,
                        'PER_100_G', 'CURATED', ?, 1, true, 0)
                """, foodPublicId.toString(), categoryId,
                FOOD_SOURCE_REFERENCE);
        Long foodId = jdbc.queryForObject(
                """
                select id from foods
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, foodPublicId.toString());
        UUID ingredientPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, food_category_id,
                     default_food_id, default_unit_id, is_staple,
                     is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9 Ingredient', ?,
                        ?, ?, false, true, 0)
                """, ingredientPublicId.toString(),
                INGREDIENT_CODE_PREFIX + ingredientPublicId,
                categoryId, foodId, unitId);
        Long ingredientId = jdbc.queryForObject(
                "select id from ingredients where code = ?", Long.class,
                INGREDIENT_CODE_PREFIX + ingredientPublicId);
        UUID secondIngredientPublicId = UUID.randomUUID();
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, food_category_id,
                     default_food_id, default_unit_id, is_staple,
                     is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P9 Ingredient Two', ?,
                        ?, ?, false, true, 0)
                """, secondIngredientPublicId.toString(),
                INGREDIENT_CODE_PREFIX + secondIngredientPublicId,
                categoryId, foodId, unitId);
        Long secondIngredientId = jdbc.queryForObject(
                "select id from ingredients where code = ?", Long.class,
                INGREDIENT_CODE_PREFIX + secondIngredientPublicId);
        return new CatalogFixture(
                foodId, foodPublicId, ingredientId, secondIngredientId, unitId);
    }

    private Long recipeId(UUID publicId) {
        return jdbc.queryForObject("""
                select id from recipes where public_id = unhex(replace(?, '-', ''))
                """, Long.class, publicId.toString());
    }

    private void assignTag(UUID recipePublicId, String tagCode) {
        assignTag(recipeId(recipePublicId), tagCode);
    }

    private void assignTag(Long recipeId, String tagCode) {
        Long tagId = jdbc.queryForObject(
                "select id from recipe_tags where code = ?", Long.class, tagCode);
        jdbc.update("""
                insert into recipe_tag_assignments (recipe_id, tag_id)
                values (?, ?)
                """, recipeId, tagId);
    }

    private void assignMealSlot(UUID recipePublicId, String slotCode) {
        Long recipeId = recipeId(recipePublicId);
        Long slotId = jdbc.queryForObject(
                "select id from meal_slot_types where code = ?", Long.class,
                slotCode);
        jdbc.update("""
                insert into recipe_meal_slot_types (recipe_id, meal_slot_type_id)
                values (?, ?)
                """, recipeId, slotId);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime publishedAt() {
        return LocalDateTime.of(2026, 9, 19, 9, 0);
    }

    private static LocalDateTime archivedAt() {
        return LocalDateTime.of(2026, 9, 19, 10, 0);
    }

    private record CatalogFixture(
            Long foodId,
            UUID foodPublicId,
            Long ingredientId,
            Long secondIngredientId,
            Long unitId) {
    }
}
