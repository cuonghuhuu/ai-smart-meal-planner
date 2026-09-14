    package com.smartmealplanner.food;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitRepository;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
@Import(BackendFoundationIT.EmptyDatabaseProof.class)
class BackendFoundationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p3_foundation")
            .withUsername("p3_test")
            .withPassword(UUID.randomUUID().toString())
            .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @Autowired FoodRepository foods;
    @Autowired FoodCategoryRepository categories;
    @Autowired NutrientRepository nutrients;
    @Autowired MeasurementUnitRepository measurementUnits;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @PersistenceContext EntityManager entityManager;
    private static final Map<String, String> FLYWAY_SCHEMA = new LinkedHashMap<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class EmptyDatabaseProof {
        @Bean
        FlywayMigrationStrategy proveEmptyThenMigrate() {
            return migration -> {
                JdbcTemplate before = new JdbcTemplate(migration.getConfiguration().getDataSource());
                assertThat(before.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()", Integer.class))
                        .as("empty MySQL database before Flyway").isZero();
                var result = migration.migrate();
                assertThat(result.migrationsExecuted).isEqualTo(2);
                assertThat(result.success).isTrue();
                for (String table : before.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class)) {
                    FLYWAY_SCHEMA.put(table, before.queryForMap("SHOW CREATE TABLE `" + table + "`").get("Create Table").toString());
                }
            };
        }
    }

    @Test
    void contextFlywayAndHibernateValidateV001() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(Arrays.stream(flyway.info().applied()).map(info -> info.getScript()).toList())
                .containsExactly("V001__initial_schema.sql", "R001__reference_data.sql");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(emf.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
        // Compare Flyway's definitions captured before JPA bootstrap to the live schema.
        // AUTO_INCREMENT counters may change in persistence tests, but DDL must not.
        FLYWAY_SCHEMA.forEach((table, definition) -> assertThat(
                jdbc.queryForMap("SHOW CREATE TABLE `" + table + "`").get("Create Table").toString()
                        .replaceAll(" AUTO_INCREMENT=\\d+", ""))
                .isEqualTo(definition.replaceAll(" AUTO_INCREMENT=\\d+", "")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(53);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'", Integer.class)).isEqualTo(125);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT table_name, index_name) FROM information_schema.statistics WHERE table_schema = DATABASE() AND index_type = 'FULLTEXT'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND extra LIKE '%STORED GENERATED%'", Integer.class)).isEqualTo(3);
    }

    @Test
    void referenceSeedIsIdempotentAndPreservesIds() throws Exception {
        Map<String, Integer> counts = Map.ofEntries(
                Map.entry("roles", 2), Map.entry("measurement_units", 16), Map.entry("nutrients", 16),
                Map.entry("activity_levels", 5), Map.entry("nutrition_goals", 6), Map.entry("dietary_preferences", 11),
                Map.entry("allergens", 14), Map.entry("meal_slot_types", 6), Map.entry("food_categories", 26),
                Map.entry("recipe_tags", 23), Map.entry("ai_score_components", 7),
                Map.entry("notification_types", 6), Map.entry("ingredient_groups", 8));
        Map<String, Object> before = new LinkedHashMap<>();
        counts.forEach((table, count) -> {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isEqualTo(count);
            before.put(table, jdbc.queryForList("SELECT id, code FROM " + table + " ORDER BY code"));
        });
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/R001__reference_data.sql"));
        }
        counts.forEach((table, count) -> assertThat(jdbc.queryForList("SELECT id, code FROM " + table + " ORDER BY code"))
                .isEqualTo(before.get(table)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void persistsCompleteFoodMappingWithUuidCategoryHierarchyAndDatabaseTimestamps() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            FoodCategory category = categories.findByCode("VEG_LEAFY").orElseThrow();
            assertThat(category.parentCategory().code()).isEqualTo("VEGETABLES");
            assertThat(category.displayName()).isEqualTo("Leafy greens");
            assertThat(category.description()).isNotBlank();
            assertThat(category.createdAt()).isNotNull();
            assertThat(category.updatedAt()).isNotNull();

            Food food = foods.saveAndFlush(new Food(
                    "P7-" + UUID.randomUUID(),
                    "P7 persistence probe",
                    "P7 brand",
                    category,
                    "Mapped V001 scalar fields",
                    NutritionBasis.PER_100_ML,
                    new BigDecimal("1.0345"),
                    FoodSource.IMPORTED,
                    "USDA FDC test reference"));
            entityManager.refresh(food);

            assertThat(food.internalId()).isPositive();
            assertThat(food.code()).startsWith("P7-");
            assertThat(food.displayName()).isEqualTo("P7 persistence probe");
            assertThat(food.brand()).isEqualTo("P7 brand");
            assertThat(food.description()).isEqualTo("Mapped V001 scalar fields");
            assertThat(food.nutritionBasis()).isEqualTo(NutritionBasis.PER_100_ML);
            assertThat(food.densityGPerMl()).isEqualByComparingTo("1.0345");
            assertThat(food.source()).isEqualTo(FoodSource.IMPORTED);
            assertThat(food.sourceReference()).isEqualTo("USDA FDC test reference");
            assertThat(food.revision()).isEqualTo(1);
            assertThat(food.isActive()).isTrue();
            assertThat(food.retiredAt()).isNull();
            assertThat(food.createdAt()).isNotNull();
            assertThat(food.updatedAt()).isNotNull();
            assertThat(food.publicIdBytes()).hasSize(16);
            assertThat(foods.findByPublicId(food.publicIdBytes())).contains(food);
            assertThat(food.category().code()).isEqualTo("VEG_LEAFY");

            var row = jdbc.queryForMap("""
                    SELECT BIN_TO_UUID(public_id) AS public_uuid, nutrition_basis,
                           density_g_per_ml, source, revision,
                           retired_at, created_at, updated_at
                    FROM foods
                    WHERE id = ?
                    """, food.internalId());
            assertThat(row.get("public_uuid")).isEqualTo(food.publicId().toString());
            assertThat(row.get("nutrition_basis")).isEqualTo("PER_100_ML");
            assertThat((BigDecimal) row.get("density_g_per_ml"))
                    .isEqualByComparingTo("1.0345");
            assertThat(row.get("source")).isEqualTo("IMPORTED");
            assertThat(((Number) row.get("revision")).longValue()).isEqualTo(1L);            assertThat(jdbc.queryForObject(
                    "SELECT is_active FROM foods WHERE id = ?",
                    Boolean.class,
                    food.internalId())).isTrue();
            assertThat(row.get("retired_at")).isNull();
            assertThat(row.get("created_at")).isNotNull();
            assertThat(row.get("updated_at")).isNotNull();
            status.setRollbackOnly();
        });
    }

    @Test
    void persistsNullableFoodFieldsAndRejectsInvalidDomainState() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Food food = foods.saveAndFlush(new Food(
                    null,
                    "P7 nullable fields probe",
                    null,
                    null,
                    null,
                    NutritionBasis.PER_100_G,
                    null,
                    FoodSource.CURATED,
                    null));

            Long foodId = food.internalId();
            entityManager.clear();
            food = foods.findById(foodId).orElseThrow();

            assertThat(food.code()).isNull();
            assertThat(food.brand()).isNull();
            assertThat(food.category()).isNull();
            assertThat(food.description()).isNull();
            assertThat(food.densityGPerMl()).isNull();
            assertThat(food.sourceReference()).isNull();
            status.setRollbackOnly();
        });

        assertThatThrownBy(() -> new Food(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid displayName");
        assertThatThrownBy(() -> new Food(
                null,
                "P7 invalid basis",
                null,
                null,
                null,
                null,
                null,
                FoodSource.CURATED,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("nutritionBasis is required");
        assertThatThrownBy(() -> new Food(
                null,
                "P7 invalid source",
                null,
                null,
                null,
                NutritionBasis.PER_100_G,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source is required");
        assertThatThrownBy(() -> new Food(
                null,
                "P7 invalid density",
                null,
                null,
                null,
                NutritionBasis.PER_100_G,
                new BigDecimal("25"),
                FoodSource.CURATED,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("densityGPerMl must be greater than zero and less than 25");
    }

    @Test
    void retirementLifecycleKeepsProvenanceRevisionSeparateFromOptimisticVersion() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Food food = foods.saveAndFlush(new Food("P7 lifecycle probe", null));
            long initialVersion = food.version();
            LocalDateTime retiredAt = LocalDateTime.of(2026, 9, 15, 10, 30);

            food.retire(retiredAt);
            foods.flush();
            entityManager.refresh(food);

            assertThat(food.isActive()).isFalse();
            assertThat(food.retiredAt()).isEqualTo(retiredAt);
            assertThat(food.revision()).isEqualTo(1);
            assertThat(food.version()).isEqualTo(initialVersion + 1);

            food.reactivate();
            foods.flush();
            entityManager.refresh(food);

            assertThat(food.isActive()).isTrue();
            assertThat(food.retiredAt()).isNull();
            assertThat(food.revision()).isEqualTo(1);
            assertThat(food.version()).isEqualTo(initialVersion + 2);
            status.setRollbackOnly();
        });

        Food activeFood = new Food("P7 lifecycle validation", null);
        assertThatThrownBy(() -> activeFood.retire(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retiredAt is required");
        activeFood.retire(LocalDateTime.of(2026, 9, 15, 10, 30));
        assertThatThrownBy(() -> activeFood.retire(LocalDateTime.of(2026, 9, 15, 10, 31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Food is already retired");
    }

    @Test
    void foodNutrientsPersistWithCompositeIdentityPrecisionAndReferenceCodes() {
        Long foodId = new TransactionTemplate(transactions).execute(status -> {
            Nutrient energy = nutrients.findByCode("ENERGY").orElseThrow();
            Nutrient protein = nutrients.findByCode("PROTEIN").orElseThrow();
            Nutrient fat = nutrients.findByCode("FAT_TOTAL").orElseThrow();

            Food food = new Food("P7 food nutrient persistence probe", null);
            food.addNutrient(
                    energy,
                    new BigDecimal("123.4567"),
                    FoodNutrientDataQuality.ANALYTICAL);
            food.addNutrient(
                    protein,
                    new BigDecimal("12.3456"),
                    FoodNutrientDataQuality.CALCULATED);
            food.addNutrient(
                    fat,
                    new BigDecimal("4.5678"),
                    FoodNutrientDataQuality.ESTIMATED);

            return foods.saveAndFlush(food).internalId();
        });

        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Food food = foods.findById(foodId).orElseThrow();
                Nutrient energy = nutrients.findByCode("ENERGY").orElseThrow();
                Nutrient protein = nutrients.findByCode("PROTEIN").orElseThrow();
                Nutrient fat = nutrients.findByCode("FAT_TOTAL").orElseThrow();

                assertThat(food.nutrientFacts()).hasSize(3);
                assertThat(food.nutrientFacts())
                        .extracting(fact -> fact.nutrient().code())
                        .containsExactlyInAnyOrder("ENERGY", "PROTEIN", "FAT_TOTAL");

                FoodNutrient energyFact = food.nutrientFacts().stream()
                        .filter(fact -> fact.nutrient().code().equals("ENERGY"))
                        .findFirst()
                        .orElseThrow();
                FoodNutrient proteinFact = food.nutrientFacts().stream()
                        .filter(fact -> fact.nutrient().code().equals("PROTEIN"))
                        .findFirst()
                        .orElseThrow();
                FoodNutrient fatFact = food.nutrientFacts().stream()
                        .filter(fact -> fact.nutrient().code().equals("FAT_TOTAL"))
                        .findFirst()
                        .orElseThrow();

                assertThat(energyFact.id())
                        .isEqualTo(new FoodNutrientId(foodId, energy.id()));
                assertThat(energyFact.food().internalId()).isEqualTo(foodId);
                assertThat(energyFact.amount()).isEqualByComparingTo("123.4567");
                assertThat(energyFact.dataQuality())
                        .isEqualTo(FoodNutrientDataQuality.ANALYTICAL);
                assertThat(energyFact.createdAt()).isNotNull();
                assertThat(energyFact.updatedAt()).isNotNull();

                assertThat(proteinFact.amount()).isEqualByComparingTo("12.3456");
                assertThat(proteinFact.dataQuality())
                        .isEqualTo(FoodNutrientDataQuality.CALCULATED);
                assertThat(proteinFact.nutrient().unit().code()).isEqualTo("g");

                assertThat(fatFact.amount()).isEqualByComparingTo("4.5678");
                assertThat(fatFact.dataQuality())
                        .isEqualTo(FoodNutrientDataQuality.ESTIMATED);
            });
        } finally {
            foods.deleteById(foodId);
        }
    }

    @Test
    void foodNutrientCompositePrimaryKeyAndNegativeAmountAreRejected() {
        Long foodId = createFoodWithNutrient("P7 food nutrient uniqueness probe");

        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        Food food = foods.findById(foodId).orElseThrow();
                        Nutrient protein = nutrients.findByCode("PROTEIN").orElseThrow();
                        entityManager.persist(new FoodNutrient(
                                food,
                                protein,
                                BigDecimal.ONE,
                                FoodNutrientDataQuality.ANALYTICAL));
                        entityManager.flush();
                    }))
                    .isInstanceOf(PersistenceException.class);

            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Nutrient protein = nutrients.findByCode("PROTEIN").orElseThrow();
                Food food = new Food("P7 invalid nutrient amount", null);

                assertThatThrownBy(() -> food.addNutrient(
                        protein,
                        new BigDecimal("-0.0001"),
                        FoodNutrientDataQuality.ANALYTICAL))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("amount must not be negative");
                assertThatThrownBy(() -> food.addNutrient(
                        protein,
                        BigDecimal.ZERO,
                        null))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("dataQuality is required");
            });
        } finally {
            foods.deleteById(foodId);
        }
    }

    @Test
    void foodServingsPersistMeasuredBridgesAndEnforceSingleDefault() {
        Long foodId = new TransactionTemplate(transactions).execute(status -> {
            MeasurementUnit grams = measurementUnits.findByCode("g").orElseThrow();
            MeasurementUnit milliliters = measurementUnits.findByCode("ml").orElseThrow();

            Food food = new Food("P7 food serving persistence probe", null);
            food.addServing(
                    "1 medium carrot",
                    BigDecimal.ONE,
                    grams,
                    new BigDecimal("61.0000"),
                    null,
                    true);
            food.addServing(
                    "1 cup chopped",
                    BigDecimal.ONE,
                    milliliters,
                    null,
                    new BigDecimal("240.0000"),
                    false);
            food.addServing(
                    "1/2 cup chopped",
                    new BigDecimal("0.5000"),
                    milliliters,
                    null,
                    new BigDecimal("120.0000"),
                    false);

            return foods.saveAndFlush(food).internalId();
        });

        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Food food = foods.findById(foodId).orElseThrow();
                assertThat(food.servings()).hasSize(3);
                assertThat(food.servings().stream()
                        .filter(FoodServing::isDefaultServing)
                        .toList())
                        .singleElement()
                        .satisfies(serving -> {
                            assertThat(serving.displayName()).isEqualTo("1 medium carrot");
                            assertThat(serving.quantity()).isEqualByComparingTo("1.0000");
                            assertThat(serving.unit().code()).isEqualTo("g");
                            assertThat(serving.gramWeight()).isEqualByComparingTo("61.0000");
                            assertThat(serving.milliliters()).isNull();
                            assertThat(serving.food().internalId()).isEqualTo(foodId);
                            assertThat(serving.createdAt()).isNotNull();
                            assertThat(serving.updatedAt()).isNotNull();
                        });
                assertThat(food.servings().stream()
                        .filter(serving -> !serving.isDefaultServing())
                        .toList())
                        .hasSize(2)
                        .allSatisfy(serving -> assertThat(serving.milliliters()).isNotNull());

                MeasurementUnit grams = measurementUnits.findByCode("g").orElseThrow();
                assertThatThrownBy(() -> food.addServing(
                        "another default",
                        BigDecimal.ONE,
                        grams,
                        BigDecimal.ONE,
                        null,
                        true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("A food may have only one default serving");
            });

            assertThatThrownBy(() -> new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> {
                        Food food = foods.findById(foodId).orElseThrow();
                        MeasurementUnit grams = measurementUnits.findByCode("g").orElseThrow();
                        entityManager.persist(new FoodServing(
                                food,
                                "database duplicate default",
                                BigDecimal.ONE,
                                grams,
                                BigDecimal.ONE,
                                null,
                                true));
                        entityManager.flush();
                    }))
                    .isInstanceOf(PersistenceException.class);
        } finally {
            foods.deleteById(foodId);
        }
    }

    @Test
    void foodServingValidationRejectsUnknownMeasuredBridge() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            MeasurementUnit grams = measurementUnits.findByCode("g").orElseThrow();
            Food food = new Food("P7 invalid serving", null);

            assertThatThrownBy(() -> food.addServing(
                    "zero quantity",
                    BigDecimal.ZERO,
                    grams,
                    BigDecimal.ONE,
                    null,
                    false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("quantity must be positive");
            assertThatThrownBy(() -> food.addServing(
                    "zero grams",
                    BigDecimal.ONE,
                    grams,
                    BigDecimal.ZERO,
                    null,
                    false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("gramWeight must be positive");
            assertThatThrownBy(() -> food.addServing(
                    "zero milliliters",
                    BigDecimal.ONE,
                    grams,
                    null,
                    BigDecimal.ZERO,
                    false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("milliliters must be positive");
            assertThatThrownBy(() -> food.addServing(
                    "missing bridge",
                    BigDecimal.ONE,
                    grams,
                    null,
                    null,
                    false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("gramWeight or milliliters is required");
        });
    }

    @Test
    void nutritionCorrectionAdvancesRevisionOnceWithoutChangingRevisionForCosmeticUpdates() {
        Long foodId = createFoodWithNutrient("P7 food revision probe");

        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Food food = foods.findById(foodId).orElseThrow();
                Nutrient protein = nutrients.findByCode("PROTEIN").orElseThrow();
                long initialVersion = food.version();

                food.correctNutrient(
                        protein,
                        new BigDecimal("99.9999"),
                        FoodNutrientDataQuality.ESTIMATED);
                food.recordNutritionCorrection();
                foods.flush();
                entityManager.refresh(food);

                assertThat(food.revision()).isEqualTo(2);
                assertThat(food.version()).isEqualTo(initialVersion + 1);
                assertThat(food.nutrientFacts()).singleElement()
                        .satisfies(fact -> {
                            assertThat(fact.amount()).isEqualByComparingTo("99.9999");
                            assertThat(fact.dataQuality())
                                    .isEqualTo(FoodNutrientDataQuality.ESTIMATED);
                        });

                food.rename("P7 food revision cosmetic rename");
                foods.flush();
                entityManager.refresh(food);

                assertThat(food.revision()).isEqualTo(2);
                assertThat(food.version()).isEqualTo(initialVersion + 2);
            });
        } finally {
            foods.deleteById(foodId);
        }
    }

    @Test
    void staleConcurrentViewCannotOverwriteCommittedUpdate() {
        Food saved = new TransactionTemplate(transactions).execute(status ->
                foods.saveAndFlush(new Food("P3 locking probe", null)));
        long initial = saved.version();
        try {
            try (var first = emf.createEntityManager(); var second = emf.createEntityManager()) {
                try {
                    first.getTransaction().begin();
                    second.getTransaction().begin();
                    Food one = first.find(Food.class, saved.internalId());
                    Food stale = second.find(Food.class, saved.internalId());
                    assertThat(one.version()).isEqualTo(initial);
                    assertThat(stale.version()).isEqualTo(initial);

                    one.rename("First writer");
                    first.getTransaction().commit();
                    assertThat(one.version()).isEqualTo(initial + 1);
                    first.close();

                    stale.rename("Stale writer");
                    assertThatThrownBy(second::flush).isInstanceOf(OptimisticLockException.class);
                } finally {
                    rollbackIfActive(first);
                    rollbackIfActive(second);
                }
            }
            Food persisted = foods.findById(saved.internalId()).orElseThrow();
            assertThat(persisted.displayName()).isEqualTo("First writer");
            assertThat(persisted.version()).isEqualTo(initial + 1);
        } finally {
            foods.deleteById(saved.internalId());
        }
    }

    private static void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.isOpen() && entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }

    private Long createFoodWithNutrient(String displayName) {
        return new TransactionTemplate(transactions).execute(status -> {
            Nutrient protein = nutrients.findByCode("PROTEIN").orElseThrow();
            Food food = new Food(displayName, null);
            food.addNutrient(
                    protein,
                    new BigDecimal("10.0000"),
                    FoodNutrientDataQuality.ANALYTICAL);
            return foods.saveAndFlush(food).internalId();
        });
    }

    @Test
    void actuatorHealthIsPublicAndDoesNotDiscloseDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
