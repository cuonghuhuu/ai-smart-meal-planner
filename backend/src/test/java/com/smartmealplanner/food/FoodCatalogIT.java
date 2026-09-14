package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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
    @Autowired NutrientRepository nutrients;
    @Autowired MeasurementUnitRepository units;
    @Autowired AllergenRepository allergens;
    @Autowired UserAccountRepository accounts;
    @Autowired PlatformTransactionManager transactions;
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
            ingredient.addAllergenFact(allergens.findByCode("SOY").orElseThrow().id(),
                    IngredientAllergenPresence.FREE_FROM, "catalog fact");
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
            assertThat(ingredient.foodMappings()).singleElement().satisfies(mapping -> {
                assertThat(mapping.preparationState()).isEqualTo(IngredientPreparationState.RAW);
                assertThat(mapping.yieldFactor()).isEqualByComparingTo("1.0000");
                assertThat(mapping.isPrimary()).isTrue();
            });
            assertThat(ingredient.allergenFacts()).singleElement().satisfies(fact -> assertThat(fact.presence()).isEqualTo(IngredientAllergenPresence.FREE_FROM));
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

    private UserAccount activeAccount() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserAccount account = accounts.saveAndFlush(new UserAccount("p7-" + suffix + "@example.com", "{noop}unused", "P7 user"));
        account.verifyEmail(LocalDateTime.now(ZoneOffset.UTC));
        return accounts.saveAndFlush(account);
    }
}
