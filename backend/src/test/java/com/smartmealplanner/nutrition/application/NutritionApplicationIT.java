package com.smartmealplanner.nutrition.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueRepository;
import com.smartmealplanner.profile.application.NutritionProfileQueryService;
import com.smartmealplanner.profile.application.NutritionProfileSnapshot;
import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.MeasurementSource;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;
import com.smartmealplanner.profile.persistence.Sex;
import com.smartmealplanner.profile.persistence.UserBodyMeasurement;
import com.smartmealplanner.profile.persistence.UserBodyMeasurementRepository;
import com.smartmealplanner.profile.persistence.UserProfile;
import com.smartmealplanner.profile.persistence.UserProfileRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class NutritionApplicationIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("p6_nutrition_application")
                    .withUsername("p6_nutrition_application_test")
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
    NutritionProfileQueryService profileQueries;

    @Autowired
    NutritionTargetApplicationService targetApplications;

    @Autowired
    NutritionTargetQueryService targetQueries;

    @Autowired
    NutritionReferenceService referenceService;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    UserProfileRepository profiles;

    @Autowired
    UserBodyMeasurementRepository measurements;

    @Autowired
    ActivityLevelRepository activityLevels;

    @Autowired
    NutritionGoalRepository nutritionGoals;

    @Autowired
    UserNutritionTargetRepository targets;

    @Autowired
    UserNutritionTargetValueRepository targetValues;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void profileBoundarySelectsLatestMeasurementOnOrBeforeEffectiveDate() {
        UserAccount account = createActiveUser();
        createProfile(account, Sex.FEMALE, "MODERATE", "MAINTAIN");
        createMeasurement(account, LocalDate.of(2026, 8, 1), "70.00");
        createMeasurement(account, LocalDate.of(2026, 9, 15), "71.00");
        createMeasurement(account, LocalDate.of(2026, 10, 1), "90.00");

        NutritionProfileSnapshot exact = profileQueries
                .findNutritionSnapshot(
                        account.internalId(),
                        LocalDate.of(2026, 9, 15))
                .orElseThrow();
        NutritionProfileSnapshot beforeExact = profileQueries
                .findNutritionSnapshot(
                        account.internalId(),
                        LocalDate.of(2026, 9, 14))
                .orElseThrow();
        NutritionProfileSnapshot beforeAll = profileQueries
                .findNutritionSnapshot(
                        account.internalId(),
                        LocalDate.of(2026, 7, 31))
                .orElseThrow();

        assertThat(exact.birthDate())
                .isEqualTo(LocalDate.of(1996, 9, 15));
        assertThat(exact.sexCode()).isEqualTo("FEMALE");
        assertThat(exact.heightCm()).isEqualByComparingTo("180.00");
        assertThat(exact.activityLevelCode()).isEqualTo("MODERATE");
        assertThat(exact.activityFactor()).isEqualByComparingTo("1.550");
        assertThat(exact.nutritionGoalCode()).isEqualTo("MAINTAIN");
        assertThat(exact.selectedWeightKg())
                .isEqualByComparingTo("71.00");
        assertThat(exact.selectedWeightMeasuredOn())
                .isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(beforeExact.selectedWeightKg())
                .isEqualByComparingTo("70.00");
        assertThat(beforeExact.selectedWeightMeasuredOn())
                .isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(beforeAll.selectedWeightKg()).isNull();
        assertThat(beforeAll.selectedWeightMeasuredOn()).isNull();
    }

    @Test
    void calculatedPersistenceUsesTheAuthoritativeProfileSnapshot() {
        UserAccount account = createActiveUser();
        createProfile(account, Sex.MALE, "MODERATE", "MAINTAIN");
        createMeasurement(account, LocalDate.of(2026, 9, 14), "80.00");
        LocalDate effectiveFrom = LocalDate.of(2026, 9, 15);

        NutritionTargetApplicationResult result = targetApplications
                .createCalculatedTarget(account.publicId(), effectiveFrom);

        assertThat(result.effectiveFrom()).isEqualTo(effectiveFrom);
        assertThat(result.effectiveTo()).isNull();
        assertThat(result.origin())
                .isEqualTo(NutritionTargetOrigin.CALCULATED);
        assertThat(result.calculationMethod())
                .isEqualTo("MIFFLIN_ST_JEOR_V1");
        assertThat(result.calculationStatus())
                .isEqualTo(com.smartmealplanner.nutrition.calculation
                        .NutritionCalculationStatus.TARGET_AVAILABLE);

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findByUserIdAndEffectiveFrom(
                                    account.internalId(),
                                    effectiveFrom)
                            .orElseThrow();
                    ActivityLevel activity = activityLevels
                            .findByCode("MODERATE")
                            .orElseThrow();
                    NutritionGoal goal = nutritionGoals
                            .findByCode("MAINTAIN")
                            .orElseThrow();

                    assertThat(target.origin())
                            .isEqualTo(NutritionTargetOrigin.CALCULATED);
                    assertThat(target.activityLevelId())
                            .isEqualTo(activity.id());
                    assertThat(target.nutritionGoalId())
                            .isEqualTo(goal.id());
                    assertThat(target.calculationMethod())
                            .isEqualTo("MIFFLIN_ST_JEOR_V1");

                    List<UserNutritionTargetValue> values = targetValues
                            .findAllByTargetIdWithNutrientAndUnit(
                                    target.id());
                    assertThat(values)
                            .extracting(value -> value.nutrient().code())
                            .containsExactly(
                                    "CARBOHYDRATE",
                                    "ENERGY",
                                    "FAT_TOTAL",
                                    "PROTEIN");
                    UserNutritionTargetValue energy = values.stream()
                            .filter(value -> "ENERGY".equals(
                                    value.nutrient().code()))
                            .findFirst()
                            .orElseThrow();
                    assertThat(energy.targetAmount())
                            .isEqualByComparingTo("2759.00");
                });
    }

    @Test
    void previewDoesNotWriteATarget() {
        UserAccount account = createActiveUser();
        createProfile(account, Sex.MALE, "MODERATE", "MAINTAIN");
        createMeasurement(account, LocalDate.of(2026, 9, 15), "80.00");
        LocalDate effectiveFrom = LocalDate.of(2026, 9, 15);

        NutritionCalculationPreviewResult result = targetApplications
                .previewCalculation(account.publicId(), effectiveFrom);

        assertThat(result.status())
                .isEqualTo(com.smartmealplanner.nutrition.calculation
                        .NutritionCalculationStatus.TARGET_AVAILABLE);
        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom)).isEmpty();
    }

    @Test
    void baselineOnlyCalculatedPersistenceWritesNothing() {
        UserAccount account = createActiveUser();
        createProfile(account, Sex.MALE, "MODERATE", "LOSE_WEIGHT");
        createMeasurement(account, LocalDate.of(2026, 9, 15), "80.00");
        LocalDate effectiveFrom = LocalDate.of(2026, 9, 15);

        assertThatThrownBy(() -> targetApplications.createCalculatedTarget(
                account.publicId(),
                effectiveFrom))
                .isInstanceOfSatisfying(
                        NutritionApplicationException.class,
                        exception -> assertThat(exception.failure())
                                .isEqualTo(
                                        NutritionApplicationFailure
                                                .GOAL_ADJUSTMENT_NOT_SUPPORTED));
        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom)).isEmpty();
    }

    @Test
    void userDefinedPersistenceWorksWithoutAutomaticCalculationContext() {
        UserAccount account = createActiveUser();
        LocalDate effectiveFrom = LocalDate.of(2026, 11, 1);
        UserDefinedNutritionTargetCommand command =
                new UserDefinedNutritionTargetCommand(
                        effectiveFrom,
                        List.of(new UserDefinedNutritionValueCommand(
                                "ENERGY",
                                new BigDecimal("1900.00"),
                                null,
                                null,
                                false)));

        NutritionTargetApplicationResult result = targetApplications
                .createUserDefinedTarget(account.publicId(), command);

        assertThat(result.origin())
                .isEqualTo(NutritionTargetOrigin.USER_DEFINED);
        assertThat(result.calculationMethod()).isNull();

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    UserNutritionTarget target = targets
                            .findByUserIdAndEffectiveFrom(
                                    account.internalId(),
                                    effectiveFrom)
                            .orElseThrow();
                    assertThat(target.activityLevelId()).isNull();
                    assertThat(target.nutritionGoalId()).isNull();
                    assertThat(target.calculationMethod()).isNull();
                    assertThat(target.origin())
                            .isEqualTo(NutritionTargetOrigin.USER_DEFINED);
                    assertThat(targetValues
                            .findAllByTargetIdWithNutrientAndUnit(target.id()))
                            .singleElement()
                            .satisfies(value -> {
                                assertThat(value.nutrient().code())
                                        .isEqualTo("ENERGY");
                                assertThat(value.targetAmount())
                                        .isEqualByComparingTo("1900.00");
                            });
                });
    }

    @Test
    void historyReturnsStableValuesAndDoesNotCrossUsers() {
        UserAccount accountA = createActiveUser();
        UserAccount accountB = createActiveUser();
        createUserDefinedTarget(accountA, LocalDate.of(2026, 10, 1), "2000.00");
        createUserDefinedTarget(accountA, LocalDate.of(2026, 12, 1), "2100.00");

        NutritionTargetHistoryPage page = targetQueries.getTargetHistory(
                accountA.publicId(),
                0,
                10);
        NutritionTargetHistoryPage otherUserPage = targetQueries
                .getTargetHistory(accountB.publicId(), 0, 10);

        assertThat(page.content())
                .extracting(NutritionTargetView::effectiveFrom)
                .containsExactly(
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 10, 1));
        assertThat(page.content().get(0).nutrientValues())
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.nutrientCode()).isEqualTo("ENERGY");
                    assertThat(value.unitCode()).isEqualTo("kcal");
                });
        assertThat(page.content().get(0).activityLevelCode()).isNull();
        assertThat(page.content().get(0).nutritionGoalCode()).isNull();
        assertThat(otherUserPage.content()).isEmpty();
    }

    @Test
    void nutrientReferenceReadUsesSeededStableVocabulary() {
        List<NutritionReferenceNutrientView> result = referenceService
                .getNutrients();

        assertThat(result)
                .extracting(NutritionReferenceNutrientView::code)
                .startsWith("ENERGY", "PROTEIN", "FAT_TOTAL");
        NutritionReferenceNutrientView energy = result.stream()
                .filter(nutrient -> "ENERGY".equals(nutrient.code()))
                .findFirst()
                .orElseThrow();
        assertThat(energy.displayName()).isEqualTo("Energy");
        assertThat(energy.nutrientKind())
                .isEqualTo(com.smartmealplanner.nutrition.persistence
                        .NutrientKind.ENERGY);
        assertThat(energy.core()).isTrue();
        assertThat(energy.displayOrder()).isEqualTo((short) 10);
        assertThat(energy.unitCode()).isEqualTo("kcal");
        assertThat(energy.unitDisplayName())
                .isEqualTo("kilocalorie");
        assertThat(NutritionReferenceNutrientView.class
                .getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("id", "nutrientId", "unitId");
    }

    private UserAccount createActiveUser() {
        return new TransactionTemplate(transactions)
                .execute(status -> {
                    UserAccount account = accounts.saveAndFlush(
                            new UserAccount(
                                    "nutrition-application-"
                                            + UUID.randomUUID()
                                            + "@example.com",
                                    "{noop}not-a-real-password",
                                    "Nutrition Application Probe"));
                    account.verifyEmail(
                            LocalDateTime.of(2026, 1, 1, 0, 0));
                    return accounts.saveAndFlush(account);
                });
    }

    private void createProfile(
            UserAccount account,
            Sex sex,
            String activityCode,
            String nutritionGoalCode) {

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    ActivityLevel activity = activityLevels
                            .findByCode(activityCode)
                            .orElseThrow();
                    NutritionGoal goal = nutritionGoals
                            .findByCode(nutritionGoalCode)
                            .orElseThrow();
                    UserProfile profile = new UserProfile(
                            account.internalId());
                    profile.update(
                            LocalDate.of(1996, 9, 15),
                            sex,
                            new BigDecimal("180.00"),
                            activity,
                            goal,
                            null,
                            null,
                            (byte) 1,
                            null,
                            null);
                    profiles.saveAndFlush(profile);
                });
    }

    private void createMeasurement(
            UserAccount account,
            LocalDate measuredOn,
            String weightKg) {

        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> measurements.saveAndFlush(
                        new UserBodyMeasurement(
                                account.internalId(),
                                measuredOn,
                                new BigDecimal(weightKg),
                                null,
                                null,
                                MeasurementSource.USER_ENTERED,
                                null)));
    }

    private void createUserDefinedTarget(
            UserAccount account,
            LocalDate effectiveFrom,
            String energy) {

        targetApplications.createUserDefinedTarget(
                account.publicId(),
                new UserDefinedNutritionTargetCommand(
                        effectiveFrom,
                        List.of(new UserDefinedNutritionValueCommand(
                                "ENERGY",
                                new BigDecimal(energy),
                                null,
                                null,
                                false))));
    }
}
