package com.smartmealplanner.nutrition.lifecycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueRepository;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
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
class NutritionTargetLifecycleIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("p6_nutrition_lifecycle")
                    .withUsername("p6_nutrition_lifecycle_test")
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
    NutritionTargetLifecycleService lifecycle;

    @Autowired
    UserNutritionTargetRepository targets;

    @Autowired
    UserNutritionTargetValueRepository targetValues;

    @Autowired
    NutrientRepository nutrients;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    ActivityLevelRepository activityLevels;

    @Autowired
    NutritionGoalRepository nutritionGoals;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void firstTargetPersistsHeaderSnapshotsAndAllValues() {
        UserAccount account = createUser();
        Long activityLevelId = activityLevels
                .findByCode("MODERATE")
                .orElseThrow()
                .id();
        Long nutritionGoalId = nutritionGoals
                .findByCode("MAINTAIN")
                .orElseThrow()
                .id();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        NutritionTargetLifecycleResult result = lifecycle.createTarget(
                command(
                        account.internalId(),
                        effectiveFrom,
                        activityLevelId,
                        nutritionGoalId,
                        "MIFFLIN_ST_JEOR_V1",
                        "Initial prepared snapshot",
                        List.of(
                                exactValue(
                                        "ENERGY",
                                        "2000.00"),
                                rangeValue(
                                        "PROTEIN",
                                        "80.00",
                                        "120.00"))));

        assertThat(result.targetId()).isNotNull();
        assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
        assertThat(result.effectiveTo()).isNull();

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findById(result.targetId())
                            .orElseThrow();

                    assertThat(target.userId())
                            .isEqualTo(account.internalId());
                    assertThat(target.effectiveFrom())
                            .isEqualTo(effectiveFrom);
                    assertThat(target.effectiveTo()).isNull();
                    assertThat(target.origin())
                            .isEqualTo(NutritionTargetOrigin.USER_DEFINED);
                    assertThat(target.activityLevelId())
                            .isEqualTo(activityLevelId);
                    assertThat(target.nutritionGoalId())
                            .isEqualTo(nutritionGoalId);
                    assertThat(target.calculationMethod())
                            .isEqualTo("MIFFLIN_ST_JEOR_V1");
                    assertThat(target.note())
                            .isEqualTo("Initial prepared snapshot");

                    List<UserNutritionTargetValue> values = targetValues
                            .findAllByTarget_Id(result.targetId());

                    assertThat(values)
                            .extracting(value -> value.nutrient().code())
                            .containsExactlyInAnyOrder(
                                    "ENERGY",
                                    "PROTEIN");
                    UserNutritionTargetValue energyValue = values.stream()
                            .filter(value -> "ENERGY".equals(
                                    value.nutrient().code()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(energyValue.targetAmount())
                            .isEqualByComparingTo("2000.00");
                });
    }

    @Test
    void appendAfterOpenTargetClosesPredecessorAtDayBeforeNewTarget() {
        UserAccount account = createUser();
        Long predecessorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 9, 1),
                null,
                NutritionTargetOrigin.CALCULATED,
                "OLD_METHOD",
                "old");
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        NutritionTargetLifecycleResult result = lifecycle.createTarget(
                command(account.internalId(), effectiveFrom, exactValue(
                        "ENERGY",
                        "2100.00")));

        assertThat(targets.findById(predecessorId).orElseThrow()
                .effectiveTo())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.effectiveTo()).isNull();
    }

    @Test
    void insertBeforeFutureTargetDerivesEndFromSuccessor() {
        UserAccount account = createUser();
        Long successorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 11, 1),
                null,
                NutritionTargetOrigin.USER_DEFINED,
                "FUTURE_METHOD",
                "future");

        NutritionTargetLifecycleResult result = lifecycle.createTarget(
                command(
                        account.internalId(),
                        LocalDate.of(2026, 10, 1),
                        exactValue("ENERGY", "2100.00")));

        assertThat(result.effectiveTo())
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(targets.findById(successorId).orElseThrow()
                .effectiveFrom())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(targets.findById(successorId).orElseThrow()
                .effectiveTo())
                .isNull();
    }

    @Test
    void insertBetweenTargetsKeepsPeriodsOrderedAndNonOverlapping() {
        UserAccount account = createUser();
        Long predecessorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                NutritionTargetOrigin.USER_DEFINED,
                "PREVIOUS_METHOD",
                "previous");
        Long successorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 11, 1),
                null,
                NutritionTargetOrigin.USER_DEFINED,
                "FUTURE_METHOD",
                "future");

        NutritionTargetLifecycleResult result = lifecycle.createTarget(
                command(
                        account.internalId(),
                        LocalDate.of(2026, 10, 1),
                        exactValue("ENERGY", "2200.00")));

        assertThat(targets.findById(predecessorId).orElseThrow()
                .effectiveTo())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.effectiveFrom())
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.effectiveTo())
                .isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(targets.findById(successorId).orElseThrow()
                .effectiveFrom())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(targets.findByUserIdOrderByEffectiveFromDesc(
                account.internalId()))
                .extracting(UserNutritionTarget::effectiveFrom)
                .containsExactly(
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 9, 1));
    }

    @Test
    void predecessorGapIsPreserved() {
        UserAccount account = createUser();
        Long predecessorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10),
                NutritionTargetOrigin.USER_DEFINED,
                "GAP_METHOD",
                "gap");

        NutritionTargetLifecycleResult result = lifecycle.createTarget(
                command(
                        account.internalId(),
                        LocalDate.of(2026, 10, 1),
                        exactValue("ENERGY", "2200.00")));

        assertThat(targets.findById(predecessorId).orElseThrow()
                .effectiveTo())
                .isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(result.effectiveTo()).isNull();
    }

    @Test
    void sameEffectiveFromIsRejectedAndExistingTargetIsUnchanged() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);
        Long existingId = createExistingTarget(
                account.internalId(),
                effectiveFrom,
                null,
                NutritionTargetOrigin.CALCULATED,
                "EXISTING_METHOD",
                "existing");

        assertThatThrownBy(() -> lifecycle.createTarget(
                command(
                        account.internalId(),
                        effectiveFrom,
                        exactValue("ENERGY", "2300.00"))))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .SAME_EFFECTIVE_DATE));

        UserNutritionTarget existing = targets.findById(existingId)
                .orElseThrow();
        assertThat(existing.effectiveTo()).isNull();
        assertThat(existing.calculationMethod())
                .isEqualTo("EXISTING_METHOD");
        assertThat(existing.note()).isEqualTo("existing");
    }

    @Test
    void unknownNutrientCodeIsRejectedBeforeHeaderPersistence() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> lifecycle.createTarget(
                command(
                        account.internalId(),
                        effectiveFrom,
                        exactValue("NOT_A_REFERENCE_NUTRIENT", "1.00"))))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .UNKNOWN_NUTRIENT_CODE));

        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom))
                .isEmpty();
    }

    @Test
    void duplicateNutrientCodeIsRejectedWithoutPersistence() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> lifecycle.createTarget(
                command(
                        account.internalId(),
                        effectiveFrom,
                        exactValue("ENERGY", "2000.00"),
                        exactValue("ENERGY", "2100.00"))))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .DUPLICATE_NUTRIENT_CODE));

        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom))
                .isEmpty();
    }

    @Test
    void invalidNutrientBoundsAreRejectedWithoutPersistence() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> lifecycle.createTarget(
                command(
                        account.internalId(),
                        effectiveFrom,
                        new NutritionTargetValueDraft(
                                "ENERGY",
                                BigDecimal.ONE,
                                new BigDecimal("10.00"),
                                new BigDecimal("9.00"),
                                false))))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .INVALID_COMMAND));

        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom))
                .isEmpty();
    }

    @Test
    void missingRequiredCommandInputIsRejectedWithoutDefaults() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> lifecycle.createTarget(null))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .INVALID_COMMAND));

        assertThatThrownBy(() -> new NutritionTargetCreateCommand(
                account.internalId(),
                effectiveFrom,
                NutritionTargetOrigin.USER_DEFINED,
                null,
                null,
                null,
                null,
                List.of()))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .INVALID_COMMAND));

        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom))
                .isEmpty();
    }

    @Test
    void multipleNutrientValuesPersistWithOneHeader() {
        UserAccount account = createUser();

        NutritionTargetLifecycleResult result = lifecycle.createTarget(
                command(
                        account.internalId(),
                        LocalDate.of(2026, 10, 1),
                        exactValue("ENERGY", "2400.00"),
                        rangeValue("CARBOHYDRATE", "270.00", "390.00"),
                        rangeValue("FAT_TOTAL", "53.33", "93.33"),
                        rangeValue("PROTEIN", "60.00", "210.00")));

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    List<UserNutritionTargetValue> values = targetValues
                            .findAllByTarget_Id(result.targetId());

                    assertThat(values)
                            .extracting(value -> value.nutrient().code())
                            .containsExactlyInAnyOrder(
                                    "ENERGY",
                                    "CARBOHYDRATE",
                                    "FAT_TOTAL",
                                    "PROTEIN");
                    assertThat(values)
                            .allSatisfy(value -> assertThat(
                                    value.isHardLimit()).isFalse());
                });
    }

    @Test
    void corruptOverlappingTimelineIsRejectedWithoutRepair() {
        UserAccount account = createUser();
        Long firstId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 9, 1),
                null,
                NutritionTargetOrigin.USER_DEFINED,
                "FIRST_METHOD",
                "first");
        Long secondId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 10, 1),
                null,
                NutritionTargetOrigin.USER_DEFINED,
                "SECOND_METHOD",
                "second");
        LocalDate newEffectiveFrom = LocalDate.of(2026, 11, 1);

        assertThatThrownBy(() -> lifecycle.createTarget(
                command(
                        account.internalId(),
                        newEffectiveFrom,
                        exactValue("ENERGY", "2400.00"))))
                .isInstanceOfSatisfying(
                        NutritionTargetLifecycleException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionTargetLifecycleFailure
                                                .CORRUPTED_TIMELINE));

        assertThat(targets.findById(firstId).orElseThrow().effectiveTo())
                .isNull();
        assertThat(targets.findById(secondId).orElseThrow().effectiveTo())
                .isNull();
        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                newEffectiveFrom))
                .isEmpty();
    }

    @Test
    void existingProvenanceAndValuesRemainUnchangedWhenInsertingBetweenTargets() {
        UserAccount account = createUser();
        Long predecessorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                NutritionTargetOrigin.CALCULATED,
                "PREVIOUS_METHOD",
                "previous note");
        Long successorId = createExistingTarget(
                account.internalId(),
                LocalDate.of(2026, 11, 1),
                null,
                NutritionTargetOrigin.ADJUSTED,
                "FUTURE_METHOD",
                "future note");
        createValue(predecessorId, "ENERGY", "1800.00");
        createValue(successorId, "ENERGY", "2600.00");

        lifecycle.createTarget(
                command(
                        account.internalId(),
                        LocalDate.of(2026, 10, 1),
                        exactValue("ENERGY", "2200.00")));

        UserNutritionTarget predecessor = targets.findById(predecessorId)
                .orElseThrow();
        UserNutritionTarget successor = targets.findById(successorId)
                .orElseThrow();

        assertThat(predecessor.origin())
                .isEqualTo(NutritionTargetOrigin.CALCULATED);
        assertThat(predecessor.calculationMethod())
                .isEqualTo("PREVIOUS_METHOD");
        assertThat(predecessor.note()).isEqualTo("previous note");
        assertThat(predecessor.effectiveTo())
                .isEqualTo(LocalDate.of(2026, 9, 30));
        assertValueAmount(predecessorId, "ENERGY", "1800.00");

        assertThat(successor.origin())
                .isEqualTo(NutritionTargetOrigin.ADJUSTED);
        assertThat(successor.calculationMethod())
                .isEqualTo("FUTURE_METHOD");
        assertThat(successor.note()).isEqualTo("future note");
        assertThat(successor.effectiveFrom())
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(successor.effectiveTo()).isNull();
        assertValueAmount(successorId, "ENERGY", "2600.00");
    }

    @Test
    void valuePersistenceFailureRollsBackTheNewHeader() {
        UserAccount account = createUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> lifecycle.createTarget(
                command(
                        account.internalId(),
                        effectiveFrom,
                        exactValue("ENERGY", "100000000.0000"))))
                .isInstanceOf(DataAccessException.class);

        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom))
                .isEmpty();
    }

    private NutritionTargetCreateCommand command(
            Long userId,
            LocalDate effectiveFrom,
            NutritionTargetValueDraft... values) {

        return command(
                userId,
                effectiveFrom,
                null,
                null,
                null,
                null,
                List.of(values));
    }

    private NutritionTargetCreateCommand command(
            Long userId,
            LocalDate effectiveFrom,
            Long activityLevelId,
            Long nutritionGoalId,
            String calculationMethod,
            String note,
            List<NutritionTargetValueDraft> values) {

        return new NutritionTargetCreateCommand(
                userId,
                effectiveFrom,
                NutritionTargetOrigin.USER_DEFINED,
                activityLevelId,
                nutritionGoalId,
                calculationMethod,
                note,
                values);
    }

    private NutritionTargetValueDraft exactValue(
            String nutrientCode,
            String amount) {

        return new NutritionTargetValueDraft(
                nutrientCode,
                new BigDecimal(amount),
                null,
                null,
                false);
    }

    private NutritionTargetValueDraft rangeValue(
            String nutrientCode,
            String minAmount,
            String maxAmount) {

        return new NutritionTargetValueDraft(
                nutrientCode,
                null,
                new BigDecimal(minAmount),
                new BigDecimal(maxAmount),
                false);
    }

    private UserAccount createUser() {
        return new TransactionTemplate(transactions)
                .execute(status -> accounts.saveAndFlush(
                        new UserAccount(
                                "nutrition-lifecycle-"
                                        + UUID.randomUUID()
                                        + "@example.com",
                                "{noop}not-a-real-password",
                                "Nutrition Lifecycle Probe")));
    }

    private Long createExistingTarget(
            Long userId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            NutritionTargetOrigin origin,
            String calculationMethod,
            String note) {

        return new TransactionTemplate(transactions)
                .execute(status -> targets.saveAndFlush(
                        new UserNutritionTarget(
                                userId,
                                effectiveFrom,
                                effectiveTo,
                                origin,
                                null,
                                null,
                                calculationMethod,
                                note))
                        .id());
    }

    private void createValue(
            Long targetId,
            String nutrientCode,
            String amount) {

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findById(targetId)
                            .orElseThrow();
                    Nutrient nutrient = nutrients
                            .findByCode(nutrientCode)
                            .orElseThrow();

                    targetValues.saveAndFlush(
                            new UserNutritionTargetValue(
                                    target,
                                    nutrient,
                                    new BigDecimal(amount),
                                    null,
                                    null,
                                    false));
                });
    }

    private void assertValueAmount(
            Long targetId,
            String nutrientCode,
            String expectedAmount) {

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTargetValue value = targetValues
                            .findAllByTarget_Id(targetId)
                            .stream()
                            .filter(candidate -> nutrientCode.equals(
                                    candidate.nutrient().code()))
                            .findFirst()
                            .orElseThrow();

                    assertThat(value.targetAmount())
                            .isEqualByComparingTo(expectedAmount);
                });
    }
}
