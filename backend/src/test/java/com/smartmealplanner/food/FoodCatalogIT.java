package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitRepository;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;
import com.smartmealplanner.profile.persistence.AllergenRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MySQL coverage for P7 catalog mappings, FULLTEXT reads, and user isolation. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class FoodCatalogIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p7_catalog")
            .withUsername("p7_catalog_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired FoodRepository foods;
    @Autowired FoodCategoryRepository categories;
    @Autowired IngredientRepository ingredients;
    @Autowired IngredientAliasRepository aliases;
    @Autowired IngredientFoodRepository ingredientFoods;
    @Autowired NutrientRepository nutrients;
    @Autowired MeasurementUnitRepository units;
    @Autowired AllergenRepository allergens;
    @Autowired UserAccountRepository accounts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired IngredientConversionService conversionService;
    @Autowired MockMvc mvc;

    @Test
    void ingredientAggregateMapsAliasesFoodFactsAllergensConversionsAndRetirement() {
        Long ingredientId = new TransactionTemplate(transactions).execute(status -> {
            FoodCategory category = categories.findByCode("VEG_LEAFY").orElseThrow();
            Food food = foods.saveAndFlush(new Food("p7-food-" + UUID.randomUUID(), "P7 spinach raw", null,
                    category, null, NutritionBasis.PER_100_G, null, FoodSource.CURATED, null));
            Ingredient ingredient = ingredients.saveAndFlush(new Ingredient("p7-spinach-" + UUID.randomUUID(),
                    "P7 Spinach", category, food, units.findByCode("g").orElseThrow(),
                    new BigDecimal("32.5000"), (short) 7, true));
            ingredient.addAlias("P7 baby spinach " + ingredient.publicId(), "en");
            ingredient.addFoodMapping(food, IngredientPreparationState.RAW, BigDecimal.ONE, true);
            for (IngredientPreparationState state : List.of(
                    IngredientPreparationState.COOKED,
                    IngredientPreparationState.DRIED,
                    IngredientPreparationState.CANNED,
                    IngredientPreparationState.FROZEN,
                    IngredientPreparationState.UNSPECIFIED)) {
                Food representation = foods.saveAndFlush(new Food(
                        "p7-food-" + state + "-" + UUID.randomUUID(),
                        "P7 spinach " + state,
                        null,
                        category,
                        null,
                        NutritionBasis.PER_100_G,
                        null,
                        FoodSource.CURATED,
                        null));
                ingredient.addFoodMapping(representation, state, BigDecimal.ONE, false);
            }
            List<IngredientAllergenPresence> presences = List.of(
                    IngredientAllergenPresence.CONTAINS,
                    IngredientAllergenPresence.MAY_CONTAIN,
                    IngredientAllergenPresence.FREE_FROM);
            List<Long> allergenIds = allergens.findAll().stream()
                    .limit(presences.size())
                    .map(allergen -> allergen.id())
                    .toList();
            for (int index = 0; index < presences.size(); index++) {
                ingredient.addAllergenFact(allergenIds.get(index), presences.get(index), "catalog fact");
            }
            ingredient.addUnitConversion(units.findByCode("piece").orElseThrow(), BigDecimal.ONE,
                    units.findByCode("g").orElseThrow(), new BigDecimal("32.5000"),
                    IngredientUnitConversionConfidence.MEASURED, "weighed");
            ingredients.flush();
            ingredient.retire(LocalDateTime.of(2026, 9, 15, 8, 0));
            ingredients.flush();
            assertThat(ingredient.isActive()).isFalse();
            assertThat(ingredient.retiredAt()).isNotNull();
            ingredient.reactivate();
            ingredients.flush();
            return ingredient.internalId();
        });
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient ingredient = ingredients.findById(ingredientId).orElseThrow();
            assertThat(ingredient.publicIdBytes()).hasSize(16);
            assertThat(ingredients.findByPublicId(ingredient.publicIdBytes())).isPresent();
            assertThat(ingredient.category().code()).isEqualTo("VEG_LEAFY");
            assertThat(ingredient.defaultFood().displayName()).isEqualTo("P7 spinach raw");
            assertThat(ingredient.defaultUnit().code()).isEqualTo("g");
            assertThat(ingredient.pieceGramWeight()).isEqualByComparingTo("32.5000");
            assertThat(ingredient.typicalShelfLifeDays()).isEqualTo((short) 7);
            assertThat(ingredient.isStaple()).isTrue();
            assertThat(ingredient.aliases()).singleElement().satisfies(alias -> assertThat(alias.aliasLocale()).isEqualTo("en"));
            assertThat(ingredient.foodMappings())
                    .extracting(IngredientFood::preparationState)
                    .containsExactlyInAnyOrderElementsOf(List.of(IngredientPreparationState.values()));
            assertThat(ingredient.foodMappings().stream().filter(IngredientFood::isPrimary).toList())
                    .singleElement()
                    .satisfies(mapping -> assertThat(mapping.preparationState()).isEqualTo(IngredientPreparationState.RAW));
            assertThat(ingredient.allergenFacts())
                    .extracting(IngredientAllergen::presence)
                    .containsExactlyInAnyOrder(
                            IngredientAllergenPresence.CONTAINS,
                            IngredientAllergenPresence.MAY_CONTAIN,
                            IngredientAllergenPresence.FREE_FROM);
            assertThat(ingredient.unitConversions()).singleElement().satisfies(conversion -> assertThat(conversion.toQuantity()).isEqualByComparingTo("32.5000"));
            assertThat(ingredient.createdAt()).isNotNull();
            assertThat(ingredient.updatedAt()).isNotNull();
            assertThat(ingredient.isActive()).isTrue();
        });
    }

    @Test
    void aggregateValidationRefusesDuplicatePrimaryAndUnknownConversionFacts() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Food first = foods.saveAndFlush(new Food("P7 first " + UUID.randomUUID(), null));
            Food second = foods.saveAndFlush(new Food("P7 second " + UUID.randomUUID(), null));
            Ingredient ingredient = ingredients.saveAndFlush(new Ingredient("p7-validation-" + UUID.randomUUID(),
                    "P7 validation", null, first, null, null, null, false));
            ingredient.addFoodMapping(first, IngredientPreparationState.UNSPECIFIED, BigDecimal.ONE, true);
            assertThatThrownBy(() -> ingredient.addFoodMapping(second, IngredientPreparationState.COOKED, BigDecimal.ONE, true))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ingredient.addUnitConversion(units.findByCode("g").orElseThrow(), BigDecimal.ZERO,
                    units.findByCode("ml").orElseThrow(), BigDecimal.ONE, IngredientUnitConversionConfidence.REFERENCE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void authenticatedCatalogApisUseFullTextAndKeepPreferencesPrivate() throws Exception {
        FoodCategory category = categories.findByCode("VEG_LEAFY").orElseThrow();
        Food food = foods.saveAndFlush(new Food("p7-api-" + UUID.randomUUID(), "P7 Fulltext Spinach", "P7", category,
                "catalog api", NutritionBasis.PER_100_G, null, FoodSource.CURATED, null));
        food.addNutrient(nutrients.findByCode("PROTEIN").orElseThrow(), new BigDecimal("2.8600"), FoodNutrientDataQuality.ANALYTICAL);
        food.addServing("1 cup", BigDecimal.ONE, units.findByCode("g").orElseThrow(), new BigDecimal("30.0000"), null, true);
        foods.saveAndFlush(food);
        Ingredient ingredient = ingredients.saveAndFlush(new Ingredient("p7-api-ingredient-" + UUID.randomUUID(), "P7 Spinach", category,
                food, units.findByCode("g").orElseThrow(), null, null, false));
        UserAccount owner = activeAccount();
        UserAccount other = activeAccount();

        mvc.perform(get("/api/v1/foods")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/foods").param("q", "Fulltext").with(jwt().jwt(jwt -> jwt.subject(owner.publicId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].displayName").value("P7 Fulltext Spinach"))
                .andExpect(jsonPath("$.content[0].id").doesNotExist());
        mvc.perform(get("/api/v1/foods/{id}", food.publicId()).with(jwt().jwt(jwt -> jwt.subject(owner.publicId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nutrients[0].nutrientCode").value("PROTEIN"))
                .andExpect(jsonPath("$.servings[0].unitCode").value("g"));
        mvc.perform(get("/api/v1/ingredients/{id}", ingredient.publicId()).with(jwt().jwt(jwt -> jwt.subject(owner.publicId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.defaultFoodPublicId").value(food.publicId().toString()))
                .andExpect(jsonPath("$.id").doesNotExist());
        String replacement = "{\"ingredients\":[{\"ingredientPublicId\":\"" + ingredient.publicId() + "\",\"strength\":\"AVOID\"}]}";
        mvc.perform(put("/api/v1/me/disliked-ingredients").with(jwt().jwt(jwt -> jwt.subject(owner.publicId().toString()))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(replacement))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ingredientPublicId").value(ingredient.publicId().toString()))
                .andExpect(jsonPath("$[0].strength").value("AVOID")).andExpect(jsonPath("$[0].id").doesNotExist());
        mvc.perform(get("/api/v1/me/disliked-ingredients").with(jwt().jwt(jwt -> jwt.subject(other.publicId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void catalogValidatesPaginationAndCategoriesAndExcludesRetiredRows() throws Exception {
        UserAccount owner = activeAccount();
        FoodCategory leafy = categories.findByCode("VEG_LEAFY").orElseThrow();
        FoodCategory fruits = categories.findByCode("FRUITS").orElseThrow();
        String token = "p7-retirement-" + UUID.randomUUID().toString().replace("-", "");
        Food activeFood = persistFood(token + " active food", leafy);
        Food retiredFood = persistFood(token + " retired food", leafy);
        retireFood(retiredFood.internalId());
        Ingredient activeIngredient = persistIngredient(token + " active ingredient", leafy, activeFood);
        Ingredient retiredIngredient = persistIngredient(token + " retired ingredient", leafy, activeFood);
        retireIngredient(retiredIngredient.internalId());

        mvc.perform(get("/api/v1/foods").param("q", token).param("categoryCode", "VEG_LEAFY")
                        .with(jwtFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(activeFood.publicId().toString()))
                .andExpect(jsonPath("$.content[0].id").doesNotExist());
        mvc.perform(get("/api/v1/ingredients").param("q", token).param("categoryCode", "VEG_LEAFY")
                        .with(jwtFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(activeIngredient.publicId().toString()))
                .andExpect(jsonPath("$.content[0].id").doesNotExist());
        mvc.perform(get("/api/v1/foods").param("q", token).param("categoryCode", "FRUITS")
                        .with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/ingredients").param("q", token).param("categoryCode", "FRUITS")
                        .with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        for (String path : List.of("/api/v1/foods", "/api/v1/ingredients")) {
            mvc.perform(get(path).param("page", "-1").with(jwtFor(owner)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mvc.perform(get(path).param("size", "0").with(jwtFor(owner)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mvc.perform(get(path).param("size", "101").with(jwtFor(owner)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mvc.perform(get(path).param("categoryCode", "NOT_A_CATEGORY").with(jwtFor(owner)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        mvc.perform(get("/api/v1/foods/{id}", retiredFood.publicId()).with(jwtFor(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
        mvc.perform(get("/api/v1/ingredients/{id}", retiredIngredient.publicId()).with(jwtFor(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/foods/{id}", UUID.randomUUID()).with(jwtFor(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
        mvc.perform(get("/api/v1/ingredients/{id}", UUID.randomUUID()).with(jwtFor(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/foods/not-a-uuid").with(jwtFor(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/ingredients/not-a-uuid").with(jwtFor(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/reference/food-categories").with(jwtFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Beverages"))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    @Test
    void fullTextAndExactAliasSearchRespectDeterministicPagination() throws Exception {
        UserAccount owner = activeAccount();
        FoodCategory category = categories.findByCode("VEG_LEAFY").orElseThrow();
        String foodToken = "P7foodpagination" + UUID.randomUUID().toString().replace("-", "");
        persistFood(foodToken + " A", category);
        persistFood(foodToken + " B", category);
        String ingredientToken = "P7ingredientpagination" + UUID.randomUUID().toString().replace("-", "");
        Food defaultFood = persistFood(ingredientToken + " food", category);
        persistIngredient(ingredientToken + " A", category, defaultFood);
        persistIngredient(ingredientToken + " B", category, defaultFood);
        Ingredient aliasIngredient = persistIngredient("P7 alias target " + UUID.randomUUID(), category, defaultFood);
        String alias = "P7 exact alias " + UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient managed = ingredients.findById(aliasIngredient.internalId()).orElseThrow();
            managed.addAlias(alias, "en");
            ingredients.flush();
        });

        mvc.perform(get("/api/v1/foods").param("q", foodToken).param("page", "0").param("size", "1").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].displayName").value(foodToken + " A"));
        mvc.perform(get("/api/v1/foods").param("q", foodToken).param("page", "1").param("size", "1").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].displayName").value(foodToken + " B"));
        mvc.perform(get("/api/v1/ingredients").param("q", ingredientToken).param("page", "0").param("size", "1").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].displayName").value(ingredientToken + " A"));
        mvc.perform(get("/api/v1/ingredients").param("q", ingredientToken).param("page", "1").param("size", "1").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].displayName").value(ingredientToken + " B"));
        mvc.perform(get("/api/v1/ingredients").param("q", alias).param("page", "0").param("size", "1").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.content[0].publicId").value(aliasIngredient.publicId().toString()));
        mvc.perform(get("/api/v1/ingredients").param("q", alias).param("page", "1").param("size", "1").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void databaseConstraintsConversionsAndPreferenceReplacementRemainSafe() throws Exception {
        FoodCategory category = categories.findByCode("VEG_LEAFY").orElseThrow();
        Food firstFood = persistFood("P7 constraint first " + UUID.randomUUID(), category);
        Food secondFood = persistFood("P7 constraint second " + UUID.randomUUID(), category);
        Ingredient first = persistIngredient("P7 constraint first ingredient " + UUID.randomUUID(), category, firstFood);
        Ingredient second = persistIngredient("P7 constraint second ingredient " + UUID.randomUUID(), category, secondFood);
        String duplicateAlias = "P7 duplicate alias " + UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient managed = ingredients.findById(first.internalId()).orElseThrow();
            managed.addAlias(duplicateAlias, "en");
            ingredients.flush();
        });
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient managed = ingredients.findById(second.internalId()).orElseThrow();
            aliases.saveAndFlush(new IngredientAlias(managed, duplicateAlias, "en"));
        })).isInstanceOf(RuntimeException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient managed = ingredients.findById(first.internalId()).orElseThrow();
            ingredientFoods.saveAndFlush(new IngredientFood(managed, firstFood, IngredientPreparationState.RAW, BigDecimal.ONE, true));
        });
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient managed = ingredients.findById(first.internalId()).orElseThrow();
            ingredientFoods.saveAndFlush(new IngredientFood(managed, secondFood, IngredientPreparationState.COOKED, BigDecimal.ONE, true));
        })).isInstanceOf(RuntimeException.class);
        assertThat(conversionService.convert(first.publicId(), "piece", BigDecimal.ONE, "g").available()).isFalse();

        UserAccount owner = activeAccount();
        String existing = "{\"ingredients\":[{\"ingredientPublicId\":\"" + first.publicId() + "\",\"strength\":\"DISLIKE\"}]}";
        mvc.perform(put("/api/v1/me/disliked-ingredients").with(jwtFor(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(existing))
                .andExpect(status().isOk());
        String duplicate = "{\"ingredients\":[{\"ingredientPublicId\":\"" + first.publicId() + "\",\"strength\":\"DISLIKE\"},{\"ingredientPublicId\":\"" + first.publicId() + "\",\"strength\":\"AVOID\"}]}";
        mvc.perform(put("/api/v1/me/disliked-ingredients").with(jwtFor(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        String invalidReplacement = "{\"ingredients\":[{\"ingredientPublicId\":\"" + second.publicId() + "\",\"strength\":\"AVOID\"},{\"ingredientPublicId\":\"" + UUID.randomUUID() + "\",\"strength\":\"DISLIKE\"}]}";
        mvc.perform(put("/api/v1/me/disliked-ingredients").with(jwtFor(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalidReplacement))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INACTIVE_INGREDIENT"));
        mvc.perform(get("/api/v1/me/disliked-ingredients").with(jwtFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ingredientPublicId").value(first.publicId().toString()))
                .andExpect(jsonPath("$[0].id").doesNotExist()).andExpect(jsonPath("$[0].ingredientId").doesNotExist());
    }

    @Test
    void ingredientUsesOptimisticLocking() {
        Ingredient saved = persistIngredient("P7 optimistic ingredient " + UUID.randomUUID(), null, null);
        try (EntityManager first = entityManagerFactory.createEntityManager(); EntityManager second = entityManagerFactory.createEntityManager()) {
            try {
                first.getTransaction().begin();
                second.getTransaction().begin();
                Ingredient current = first.find(Ingredient.class, saved.internalId());
                Ingredient stale = second.find(Ingredient.class, saved.internalId());
                current.retire(LocalDateTime.of(2026, 9, 15, 10, 0));
                first.getTransaction().commit();
                stale.retire(LocalDateTime.of(2026, 9, 15, 10, 1));
                assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
            } finally {
                if (first.getTransaction().isActive()) {
                    first.getTransaction().rollback();
                }
                if (second.getTransaction().isActive()) {
                    second.getTransaction().rollback();
                }
            }
        }
    }

    private Food persistFood(String displayName, FoodCategory category) {
        return foods.saveAndFlush(new Food("p7-hardening-" + UUID.randomUUID(), displayName, null, category,
                null, NutritionBasis.PER_100_G, null, FoodSource.CURATED, null));
    }

    private Ingredient persistIngredient(String displayName, FoodCategory category, Food defaultFood) {
        return ingredients.saveAndFlush(new Ingredient("p7-hardening-ingredient-" + UUID.randomUUID(), displayName,
                category, defaultFood, null, null, null, false));
    }

    private void retireFood(Long foodId) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Food food = foods.findById(foodId).orElseThrow();
            food.retire(LocalDateTime.of(2026, 9, 15, 9, 0));
            foods.flush();
        });
    }

    private void retireIngredient(Long ingredientId) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Ingredient ingredient = ingredients.findById(ingredientId).orElseThrow();
            ingredient.retire(LocalDateTime.of(2026, 9, 15, 9, 0));
            ingredients.flush();
        });
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UserAccount account) {
        return jwt().jwt(jwt -> jwt.subject(account.publicId().toString()));
    }

    private UserAccount activeAccount() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserAccount account = accounts.saveAndFlush(new UserAccount("p7-" + suffix + "@example.com", "{noop}unused", "P7 user"));
        account.verifyEmail(LocalDateTime.now(ZoneOffset.UTC));
        return accounts.saveAndFlush(account);
    }
}
