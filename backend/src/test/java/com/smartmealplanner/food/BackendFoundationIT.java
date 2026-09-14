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

    @Test
    void actuatorHealthIsPublicAndDoesNotDiscloseDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
