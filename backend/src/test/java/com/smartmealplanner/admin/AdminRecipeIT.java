package com.smartmealplanner.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.admin.web.AdminRecipeRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class AdminRecipeIT {

    private static final String SLUG_PREFIX = "p12-admin-recipe-";
    private static final String EMAIL_PREFIX = "p12-admin-recipe-it-";
    private static final String INGREDIENT_PREFIX = "P12_ADMIN_INGREDIENT_";
    private static final String FOOD_PREFIX = "P12_ADMIN_FOOD_";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p12_admin_recipes")
            .withUsername("p12_admin_recipes_test")
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

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanSyntheticRows() {
        jdbc.update("""
                delete from recipe_nutrition_values
                where snapshot_id in (
                    select id from recipe_nutrition_snapshots
                    where recipe_id in (select id from recipes where slug like ?))
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
                delete from food_nutrients
                where food_id in (select id from foods where code like ?)
                """, FOOD_PREFIX + "%");
        jdbc.update("delete from ingredients where code like ?", INGREDIENT_PREFIX + "%");
        jdbc.update("delete from foods where code like ?", FOOD_PREFIX + "%");
        jdbc.update("""
                delete from user_roles
                where user_id in (select id from users where email like ?)
                """, EMAIL_PREFIX + "%");
        jdbc.update("delete from users where email like ?", EMAIL_PREFIX + "%");
    }

    @Test
    void userRoleIsForbiddenAndAdminCanCreateUpdatePublishAndArchive()
        throws Exception {
        Account admin = insertAccount("admin");
        Account user = insertAccount("user", "ROLE_USER");
        CatalogFixture catalog = insertCatalogFixture();
        AdminRecipeRequest draft = draftRequest(catalog.ingredientPublicId());

        mvc.perform(get("/api/v1/admin/recipes")
                        .with(auth(user, "ROLE_USER")))
                .andExpect(status().isForbidden());

        MvcResult created = mvc.perform(post("/api/v1/admin/recipes")
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draft)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.source").value("CURATED"))
                .andExpect(jsonPath("$.createdByUserId").doesNotExist())
                .andExpect(jsonPath("$.ingredients[0].ingredientId").doesNotExist())
                .andReturn();
        UUID recipePublicId = UUID.fromString(objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("publicId").asText());

        AdminRecipeRequest updated = new AdminRecipeRequest(
                "Cơm gà quản trị đã sửa",
                draft.slug(),
                draft.summary(),
                draft.servings(),
                draft.prepMinutes(),
                draft.cookMinutes(),
                draft.difficulty(),
                draft.instructionsNote(),
                draft.imageUrl(),
                draft.ingredients(),
                draft.steps(),
                draft.tagCodes(),
                draft.mealSlotCodes());
        mvc.perform(put("/api/v1/admin/recipes/{publicId}", recipePublicId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cơm gà quản trị đã sửa"));

        mvc.perform(post("/api/v1/admin/recipes/{publicId}/publish", recipePublicId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.nutrition").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(countSnapshots(recipePublicId))
                .isEqualTo(1);

        mvc.perform(put("/api/v1/admin/recipes/{publicId}", recipePublicId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_LIFECYCLE"));

        mvc.perform(post("/api/v1/admin/recipes/{publicId}/archive", recipePublicId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mvc.perform(get("/api/v1/recipes/{publicId}", recipePublicId)
                        .with(auth(user, "ROLE_USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminListIncludesDraftPublishedAndArchivedStates()
            throws Exception {
        Account admin = insertAccount("admin");
        CatalogFixture catalog = insertCatalogFixture();
        UUID draftId = createDraft(admin, catalog.ingredientPublicId(), "one");
        UUID publishedId = createDraft(admin, catalog.ingredientPublicId(), "two");
        UUID archivedId = createDraft(admin, catalog.ingredientPublicId(), "three");

        mvc.perform(post("/api/v1/admin/recipes/{publicId}/publish", publishedId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/recipes/{publicId}/publish", archivedId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/recipes/{publicId}/archive", archivedId)
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/recipes")
                        .param("q", "P12 Admin")
                        .with(auth(admin, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(hasItem(draftId.toString())))
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(hasItem(publishedId.toString())))
                .andExpect(jsonPath("$.content[*].publicId")
                        .value(hasItem(archivedId.toString())))
                .andExpect(jsonPath("$.content[*].status")
                        .value(hasItem("DRAFT")))
                .andExpect(jsonPath("$.content[*].status")
                        .value(hasItem("PUBLISHED")))
                .andExpect(jsonPath("$.content[*].status")
                        .value(hasItem("ARCHIVED")));
    }

    private UUID createDraft(
            Account admin,
            UUID ingredientPublicId,
            String suffix) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/recipes")
                        .with(auth(admin, "ROLE_ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                draftRequest(ingredientPublicId, suffix))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.get("publicId").asText());
    }

    private AdminRecipeRequest draftRequest(UUID ingredientPublicId) {
        return draftRequest(ingredientPublicId, "one");
    }

    private AdminRecipeRequest draftRequest(UUID ingredientPublicId, String suffix) {
        return new AdminRecipeRequest(
                "P12 Admin Recipe " + suffix,
                SLUG_PREFIX + suffix + "-" + UUID.randomUUID(),
                "P12 Admin recipe fixture",
                2,
                10,
                20,
                com.smartmealplanner.recipe.RecipeDifficulty.EASY,
                null,
                null,
                List.of(new AdminRecipeRequest.Ingredient(
                        ingredientPublicId,
                        new BigDecimal("100.0000"),
                        "g",
                        null,
                        false,
                        false,
                        null)),
                List.of(new AdminRecipeRequest.Step(
                        1, "Chuẩn bị và nấu nguyên liệu.", 10)),
                List.of("MAIN_DISH"),
                List.of("LUNCH"));
    }

    private CatalogFixture insertCatalogFixture() {
        Long categoryId = jdbc.queryForObject(
                "select id from food_categories where code = 'PROTEIN'", Long.class);
        Long unitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);
        Long nutrientId = jdbc.queryForObject(
                "select id from nutrients where code = 'ENERGY'", Long.class);
        UUID foodPublicId = UUID.randomUUID();
        String foodCode = FOOD_PREFIX + foodPublicId;
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, food_category_id,
                     nutrition_basis, source, source_reference, revision,
                     is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P12 Admin Food', ?,
                        'PER_100_G', 'CURATED', 'P12 admin fixture', 1, true, 0)
                """, foodPublicId.toString(), foodCode, categoryId);
        Long foodId = jdbc.queryForObject(
                "select id from foods where code = ?", Long.class, foodCode);
        jdbc.update("""
                insert into food_nutrients (food_id, nutrient_id, amount, data_quality)
                values (?, ?, 200.0000, 'ANALYTICAL')
                """, foodId, nutrientId);

        UUID ingredientPublicId = UUID.randomUUID();
        String ingredientCode = INGREDIENT_PREFIX + ingredientPublicId;
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, food_category_id,
                     default_food_id, default_unit_id, is_staple, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P12 Admin Ingredient', ?,
                        ?, ?, false, true, 0)
                """, ingredientPublicId.toString(), ingredientCode, categoryId,
                foodId, unitId);
        return new CatalogFixture(ingredientPublicId);
    }

    private Account insertAccount(String suffix) {
        return insertAccount(suffix, "ROLE_ADMIN");
    }

    private Account insertAccount(String suffix, String role) {
        UUID publicId = UUID.randomUUID();
        String email = EMAIL_PREFIX + suffix + "-" + publicId + "@example.test";
        jdbc.update("""
                insert into users
                    (public_id, email, password_hash, display_name,
                     account_status, email_verified_at, time_zone, locale, version)
                values (unhex(replace(?, '-', '')), ?, 'test-hash',
                        'P12 Recipe Admin', 'ACTIVE', current_timestamp(6),
                        'UTC', 'vi', 0)
                """, publicId.toString(), email);
        Long internalId = jdbc.queryForObject(
                "select id from users where email = ?", Long.class, email);
        jdbc.update("""
                insert into user_roles (user_id, role_id)
                select ?, id from roles where code = ?
                """, internalId, role);
        return new Account(publicId, internalId);
    }

    private int countSnapshots(UUID recipePublicId) {
        return jdbc.queryForObject("""
                select count(*) from recipe_nutrition_snapshots
                where recipe_id = (select id from recipes
                                   where public_id = unhex(replace(?, '-', '')))
                """, Integer.class, recipePublicId.toString());
    }

    private static RequestPostProcessor auth(Account account, String role) {
        return jwt()
                .jwt(token -> token.subject(account.publicId().toString()))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private record Account(UUID publicId, Long internalId) {
    }

    private record CatalogFixture(UUID ingredientPublicId) {
    }
}
