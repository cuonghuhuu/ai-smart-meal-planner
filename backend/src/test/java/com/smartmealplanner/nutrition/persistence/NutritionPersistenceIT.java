package com.smartmealplanner.nutrition.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NutritionPersistenceIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("p6_nutrition_persistence")
                    .withUsername("p6_nutrition_test")
                    .withPassword(UUID.randomUUID().toString())
                    .withUrlParam("connectionTimeZone", "UTC");

    @DynamicPropertySource
    static void database(
            DynamicPropertyRegistry registry) {

        registry.add(
                "spring.datasource.url",
                MYSQL::getJdbcUrl);
        registry.add(
                "spring.datasource.username",
                MYSQL::getUsername);
        registry.add(
                "spring.datasource.password",
                MYSQL::getPassword);
    }

    @Autowired
    MeasurementUnitRepository measurementUnits;

    @Autowired
    NutrientRepository nutrients;

    @Autowired
    UserNutritionTargetRepository targets;

    @Autowired
    UserNutritionTargetValueRepository targetValues;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    ActivityLevelRepository activityLevels;

    @Autowired
    NutritionGoalRepository nutritionGoals;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void seededNutrientsLoadWithMeasurementUnitsAndCodeLookup() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    assertThat(nutrients
                            .findAllByOrderByDisplayOrderAscCodeAsc())
                            .hasSize(16)
                            .extracting(Nutrient::code)
                            .startsWith("ENERGY", "PROTEIN")
                            .endsWith("WATER");

                    Nutrient protein = nutrients
                            .findByCode("PROTEIN")
                            .orElseThrow();
                    MeasurementUnit grams = measurementUnits
                            .findByCode("g")
                            .orElseThrow();

                    assertThat(protein.displayName())
                            .isEqualTo("Protein");
                    assertThat(protein.nutrientKind())
                            .isEqualTo(NutrientKind.MACRONUTRIENT);
                    assertThat(protein.isCore()).isTrue();
                    assertThat(protein.unit().id())
                            .isEqualTo(grams.id());
                    assertThat(protein.unit().code())
                            .isEqualTo("g");
                    assertThat(protein.unit().displayName())
                            .isEqualTo("gram");
                });
    }

    @Test
    void targetPersistsHeaderAndReferenceSnapshots() {
        UserAccount account = createUser();
        ActivityLevel activity = activityLevels
                .findByCode("MODERATE")
                .orElseThrow();
        NutritionGoal goal = nutritionGoals
                .findByCode("MAINTAIN")
                .orElseThrow();
        LocalDate effectiveFrom = LocalDate.of(2026, 9, 15);
        LocalDate effectiveTo = LocalDate.of(2026, 9, 30);

        Long targetId = new TransactionTemplate(transactions)
                .execute(status -> {
                    UserNutritionTarget target = targets.saveAndFlush(
                            new UserNutritionTarget(
                                    account.internalId(),
                                    effectiveFrom,
                                    effectiveTo,
                                    NutritionTargetOrigin.CALCULATED,
                                    activity.id(),
                                    goal.id(),
                                    "MIFFLIN_ST_JEOR_V1",
                                    "Persistence probe"));
                    entityManager.refresh(target);
                    return target.id();
                });

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findById(targetId)
                            .orElseThrow();

                    assertThat(target.userId())
                            .isEqualTo(account.internalId());
                    assertThat(target.effectiveFrom())
                            .isEqualTo(effectiveFrom);
                    assertThat(target.effectiveTo())
                            .isEqualTo(effectiveTo);
                    assertThat(target.origin())
                            .isEqualTo(NutritionTargetOrigin.CALCULATED);
                    assertThat(target.activityLevelId())
                            .isEqualTo(activity.id());
                    assertThat(target.nutritionGoalId())
                            .isEqualTo(goal.id());
                    assertThat(target.calculationMethod())
                            .isEqualTo("MIFFLIN_ST_JEOR_V1");
                    assertThat(target.createdAt()).isNotNull();
                    assertThat(target.updatedAt()).isNotNull();

                    assertThat(targets
                            .findByUserIdAndEffectiveFrom(
                                    account.internalId(),
                                    effectiveFrom)
                            .orElseThrow()
                            .id())
                            .isEqualTo(targetId);
                    assertThat(targets
                            .findByUserIdOrderByEffectiveFromDesc(
                                    account.internalId()))
                            .extracting(UserNutritionTarget::id)
                            .containsExactly(targetId);
                    assertThat(targets
                            .findByUserIdAndEffectiveOn(
                                    account.internalId(),
                                    LocalDate.of(2026, 9, 20)))
                            .extracting(UserNutritionTarget::id)
                            .containsExactly(targetId);
                    assertThat(targets
                            .findAllByUserIdForUpdate(
                                    account.internalId()))
                            .extracting(UserNutritionTarget::id)
                            .containsExactly(targetId);
                });
    }

    @Test
    void targetValuesRoundTripCompositeIdentityAndDecimalBounds() {
        UserAccount account = createUser();
        Long targetId = createTarget(
                account.internalId(),
                LocalDate.of(2026, 10, 1));

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findById(targetId)
                            .orElseThrow();
                    Nutrient energy = nutrients
                            .findByCode("ENERGY")
                            .orElseThrow();
                    Nutrient protein = nutrients
                            .findByCode("PROTEIN")
                            .orElseThrow();

                    targetValues.saveAndFlush(
                            new UserNutritionTargetValue(
                                    target,
                                    energy,
                                    new BigDecimal("1842.3456"),
                                    new BigDecimal("1800.0000"),
                                    new BigDecimal("2000.0000"),
                                    false));
                    targetValues.saveAndFlush(
                            new UserNutritionTargetValue(
                                    target,
                                    protein,
                                    null,
                                    new BigDecimal("80.1234"),
                                    new BigDecimal("120.5678"),
                                    true));

                    entityManager.clear();

                    var values = targetValues
                            .findAllByTarget_Id(targetId);

                    assertThat(values)
                            .hasSize(2)
                            .extracting(value -> value.nutrient().code())
                            .containsExactlyInAnyOrder("ENERGY", "PROTEIN");

                    UserNutritionTargetValue energyValue = values.stream()
                            .filter(value -> value.nutrient().code().equals("ENERGY"))
                            .findFirst()
                            .orElseThrow();
                    UserNutritionTargetValue proteinValue = values.stream()
                            .filter(value -> value.nutrient().code().equals("PROTEIN"))
                            .findFirst()
                            .orElseThrow();

                    assertThat(energyValue.id().targetId())
                            .isEqualTo(targetId);
                    assertThat(energyValue.id().nutrientId())
                            .isEqualTo(energy.id());
                    assertThat(energyValue.id())
                            .isEqualTo(new UserNutritionTargetValueId(
                                    targetId,
                                    energy.id()));
                    assertThat(energyValue.target().id())
                            .isEqualTo(targetId);
                    assertThat(energyValue.targetAmount())
                            .isEqualByComparingTo("1842.3456");
                    assertThat(energyValue.minAmount())
                            .isEqualByComparingTo("1800.0000");
                    assertThat(energyValue.maxAmount())
                            .isEqualByComparingTo("2000.0000");
                    assertThat(energyValue.isHardLimit()).isFalse();

                    assertThat(proteinValue.targetAmount()).isNull();
                    assertThat(proteinValue.minAmount())
                            .isEqualByComparingTo("80.1234");
                    assertThat(proteinValue.maxAmount())
                            .isEqualByComparingTo("120.5678");
                    assertThat(proteinValue.isHardLimit()).isTrue();
                });
    }

    @Test
    void targetValueRejectsUnsavedTargetId() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    Nutrient nutrient = nutrients
                            .findByCode("PROTEIN")
                            .orElseThrow();
                    UserNutritionTarget unsavedTarget =
                            new UserNutritionTarget(
                                    1L,
                                    LocalDate.of(2026, 12, 1),
                                    null,
                                    NutritionTargetOrigin.USER_DEFINED,
                                    null,
                                    null,
                                    null,
                                    null);

                    assertThatThrownBy(() -> new UserNutritionTargetValue(
                            unsavedTarget,
                            nutrient,
                            BigDecimal.ONE,
                            null,
                            null,
                            false))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("target.id is required");
                });
    }

    @Test
    void targetValueRejectsUnsavedNutrientId() {
        UserAccount account = createUser();
        Long targetId = createTarget(
                account.internalId(),
                LocalDate.of(2027, 1, 1));

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findById(targetId)
                            .orElseThrow();
                    MeasurementUnit unit = measurementUnits
                            .findByCode("g")
                            .orElseThrow();
                    Nutrient unsavedNutrient = new Nutrient(
                            "TEST_NUTRIENT",
                            "Test nutrient",
                            unit,
                            NutrientKind.OTHER,
                            false,
                            (short) 1000);

                    assertThatThrownBy(() -> new UserNutritionTargetValue(
                            target,
                            unsavedNutrient,
                            BigDecimal.ONE,
                            null,
                            null,
                            false))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("nutrient.id is required");
                });
    }

    @Test
    void targetStartDateIsUniquePerUser() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 11, 1);
        createTarget(account.internalId(), effectiveFrom);

        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> targets.saveAndFlush(
                        new UserNutritionTarget(
                                account.internalId(),
                                effectiveFrom,
                                null,
                                NutritionTargetOrigin.USER_DEFINED,
                                null,
                                null,
                                null,
                                null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void hibernateValidationRunsAgainstTheRealMysqlSchema() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
    }

    private UserAccount createUser() {
        return new TransactionTemplate(transactions)
                .execute(status -> accounts.saveAndFlush(
                        new UserAccount(
                                "nutrition-"
                                        + UUID.randomUUID()
                                        + "@example.com",
                                "{noop}not-a-real-password",
                                "Nutrition Persistence Probe")));
    }

    private Long createTarget(
            Long userId,
            LocalDate effectiveFrom) {

        return new TransactionTemplate(transactions)
                .execute(status -> targets.saveAndFlush(
                        new UserNutritionTarget(
                                userId,
                                effectiveFrom,
                                null,
                                NutritionTargetOrigin.USER_DEFINED,
                                null,
                                null,
                                null,
                                null))
                        .id());
    }
}
