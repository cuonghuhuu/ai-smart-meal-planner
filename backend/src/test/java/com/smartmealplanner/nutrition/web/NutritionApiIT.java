package com.smartmealplanner.nutrition.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartmealplanner.auth.persistence.Role;
import com.smartmealplanner.auth.persistence.RoleRepository;
import com.smartmealplanner.auth.persistence.UserAccount;
import com.smartmealplanner.auth.persistence.UserAccountRepository;
import com.smartmealplanner.auth.persistence.UserRole;
import com.smartmealplanner.auth.persistence.UserRoleRepository;
import com.smartmealplanner.nutrition.persistence.NutritionTargetOrigin;
import com.smartmealplanner.nutrition.persistence.UserNutritionTarget;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetRepository;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValue;
import com.smartmealplanner.nutrition.persistence.UserNutritionTargetValueRepository;
import com.smartmealplanner.profile.persistence.MeasurementSource;
import com.smartmealplanner.profile.persistence.Sex;
import com.smartmealplanner.profile.web.RecordMeasurementRequest;
import com.smartmealplanner.profile.web.UpdateProfileRequest;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class NutritionApiIT {

    private static final String RAW_PASSWORD =
            "correct-horse-battery-staple";

    private static final String ROLE_USER =
            "ROLE_USER";

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("p6_nutrition_api")
                    .withUsername("p6_nutrition_api_test")
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
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    RoleRepository roles;

    @Autowired
    UserRoleRepository userRoles;

    @Autowired
    UserNutritionTargetRepository targets;

    @Autowired
    UserNutritionTargetValueRepository targetValues;

    @Test
    void unauthenticatedNutritionRoutesReturn401()
            throws Exception {

        LocalDate effectiveFrom = todayUtc();
        String effectiveDateRequest = effectiveDateRequest(effectiveFrom);
        String userDefinedRequest = userDefinedRequest(
                effectiveFrom,
                "2200.00");

        mvc.perform(post("/api/v1/me/nutrition-targets/calculate")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("invalid.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(effectiveDateRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/api/v1/me/nutrition-targets/calculated")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("invalid.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(effectiveDateRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/api/v1/me/nutrition-targets/user-defined")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer("invalid.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userDefinedRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/me/nutrition-targets/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/me/nutrition-targets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/reference/nutrients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void previewReturnsSelectedInputsAndDoesNotPersistATarget()
            throws Exception {

        UserAccount account = createActiveUser();
        String token = loginAndGetAccessToken(account);
        LocalDate effectiveFrom = todayUtc();
        prepareCalculationContext(token, effectiveFrom, "MAINTAIN");

        mvc.perform(post("/api/v1/me/nutrition-targets/calculate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(effectiveDateRequest(effectiveFrom)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom")
                        .value(effectiveFrom.toString()))
                .andExpect(jsonPath("$.calculationMethod")
                        .value("MIFFLIN_ST_JEOR_V1"))
                .andExpect(jsonPath("$.status")
                        .value("TARGET_AVAILABLE"))
                .andExpect(jsonPath("$.rmrKcal").value(1780))
                .andExpect(jsonPath("$.maintenanceEnergyKcal")
                        .value(2759))
                .andExpect(jsonPath("$.input.sex").value("MALE"))
                .andExpect(jsonPath("$.input.activityLevelCode")
                        .value("MODERATE"))
                .andExpect(jsonPath("$.input.nutritionGoalCode")
                        .value("MAINTAIN"))
                .andExpect(jsonPath("$.nutrientTargets", hasSize(4)))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.targetId").doesNotExist())
                .andExpect(jsonPath("$.input.activityLevelId").doesNotExist())
                .andExpect(jsonPath("$.input.nutritionGoalId").doesNotExist())
                .andExpect(jsonPath("$.nutrientTargets[0].nutrientId")
                        .doesNotExist());

        assertThat(targets.findByUserIdAndEffectiveFrom(
                account.internalId(),
                effectiveFrom)).isEmpty();
    }

    @Test
    void calculatedTargetIsServerCalculatedAndReturnsNoInternalIdentifiers()
            throws Exception {

        UserAccount account = createActiveUser();
        String token = loginAndGetAccessToken(account);
        LocalDate effectiveFrom = todayUtc();
        prepareCalculationContext(token, effectiveFrom, "MAINTAIN");

        mvc.perform(post("/api/v1/me/nutrition-targets/calculated")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(effectiveDateRequest(effectiveFrom)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveFrom")
                        .value(effectiveFrom.toString()))
                .andExpect(jsonPath("$.origin").value("CALCULATED"))
                .andExpect(jsonPath("$.calculationMethod")
                        .value("MIFFLIN_ST_JEOR_V1"))
                .andExpect(jsonPath("$.calculationStatus")
                        .value("TARGET_AVAILABLE"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.targetId").doesNotExist())
                .andExpect(jsonPath("$.activityLevelId").doesNotExist())
                .andExpect(jsonPath("$.nutritionGoalId").doesNotExist());

        UserNutritionTarget target = targets
                .findByUserIdAndEffectiveFrom(
                        account.internalId(),
                        effectiveFrom)
                .orElseThrow();

        assertThat(target.origin())
                .isEqualTo(NutritionTargetOrigin.CALCULATED);
        assertThat(target.calculationMethod())
                .isEqualTo("MIFFLIN_ST_JEOR_V1");
        assertThat(targetValues.findAllByTargetIdWithNutrientAndUnit(
                target.id()))
                .extracting(value -> value.nutrient().code())
                .containsExactly(
                        "CARBOHYDRATE",
                        "ENERGY",
                        "FAT_TOTAL",
                        "PROTEIN");

        UserNutritionTargetValue energy = targetValues
                .findAllByTargetIdWithNutrientAndUnit(target.id())
                .stream()
                .filter(value -> "ENERGY".equals(value.nutrient().code()))
                .findFirst()
                .orElseThrow();
        assertThat(energy.targetAmount())
                .isEqualByComparingTo("2759.00");
        assertThat(NutritionEffectiveDateRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("effectiveFrom");
    }

    @Test
    void userDefinedTargetUsesTheAuthenticatedOwnerAndRejectsSameDate()
            throws Exception {

        UserAccount accountA = createActiveUser();
        UserAccount accountB = createActiveUser();
        String tokenA = loginAndGetAccessToken(accountA);
        LocalDate effectiveFrom = todayUtc().minusDays(1);
        String request = userDefinedRequest(effectiveFrom, "2200.00");

        mvc.perform(post("/api/v1/me/nutrition-targets/user-defined")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("USER_DEFINED"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.targetId").doesNotExist())
                .andExpect(jsonPath("$.activityLevelId").doesNotExist())
                .andExpect(jsonPath("$.nutritionGoalId").doesNotExist());

        UserNutritionTarget target = targets
                .findByUserIdAndEffectiveFrom(
                        accountA.internalId(),
                        effectiveFrom)
                .orElseThrow();
        assertThat(target.origin())
                .isEqualTo(NutritionTargetOrigin.USER_DEFINED);
        assertThat(target.activityLevelId()).isNull();
        assertThat(target.nutritionGoalId()).isNull();
        assertThat(target.calculationMethod()).isNull();
        List<UserNutritionTargetValue> storedValues = targetValues
                .findAllByTargetIdWithNutrientAndUnit(target.id());
        assertThat(storedValues).hasSize(1);
        UserNutritionTargetValue storedEnergy = storedValues.get(0);
        assertThat(storedEnergy.nutrient().code()).isEqualTo("ENERGY");
        assertThat(storedEnergy.targetAmount())
                .isEqualByComparingTo("2200.00");
        assertThat(storedEnergy.minAmount()).isNull();
        assertThat(storedEnergy.maxAmount()).isNull();
        assertThat(storedEnergy.isHardLimit()).isFalse();
        assertThat(targets.findByUserIdAndEffectiveFrom(
                accountB.internalId(),
                effectiveFrom)).isEmpty();

        mvc.perform(post("/api/v1/me/nutrition-targets/user-defined")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_EFFECTIVE_DATE"));

        assertThat(UserDefinedNutritionTargetRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "userId",
                        "effectiveTo",
                        "origin",
                        "activityLevelId",
                        "nutritionGoalId",
                        "calculationMethod");
    }

    @Test
    void currentTargetUsesApplicationTimeSemanticsAndReportsAbsence()
            throws Exception {

        UserAccount account = createActiveUser();
        String token = loginAndGetAccessToken(account);

        mvc.perform(get("/api/v1/me/nutrition-targets/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_CURRENT_TARGET"))
                .andExpect(jsonPath("$.requestId").isString());

        LocalDate effectiveFrom = todayUtc();
        UserAccount otherAccount = createActiveUser();
        String otherToken = loginAndGetAccessToken(otherAccount);
        createUserDefinedTarget(otherToken, effectiveFrom, "1900.00");

        mvc.perform(get("/api/v1/me/nutrition-targets/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NO_CURRENT_TARGET"));

        createUserDefinedTarget(token, effectiveFrom, "2100.00");

        mvc.perform(get("/api/v1/me/nutrition-targets/current")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveFrom")
                        .value(effectiveFrom.toString()))
                .andExpect(jsonPath("$.origin").value("USER_DEFINED"))
                .andExpect(jsonPath("$.nutrientValues[0].nutrientCode")
                        .value("ENERGY"))
                .andExpect(jsonPath("$.nutrientValues[0].unitCode")
                        .value("kcal"))
                .andExpect(jsonPath("$.nutrientValues[0].targetAmount")
                        .value(2100.00))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.nutrientValues[0].nutrientId")
                        .doesNotExist())
                .andExpect(jsonPath("$.nutrientValues[0].unitId")
                        .doesNotExist());
    }

    @Test
    void historyIsPagedNewestFirstAndCannotCrossUsers()
            throws Exception {

        UserAccount accountA = createActiveUser();
        UserAccount accountB = createActiveUser();
        String tokenA = loginAndGetAccessToken(accountA);
        String tokenB = loginAndGetAccessToken(accountB);
        LocalDate today = todayUtc();

        createUserDefinedTarget(tokenA, today.minusDays(2), "2000.00");
        createUserDefinedTarget(tokenA, today.minusDays(1), "2100.00");
        createUserDefinedTarget(tokenB, today.minusDays(1), "1900.00");

        mvc.perform(get("/api/v1/me/nutrition-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].effectiveFrom")
                        .value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.content[1].effectiveFrom")
                        .value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].nutrientValues[0].targetId")
                        .doesNotExist());

        mvc.perform(get("/api/v1/me/nutrition-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].effectiveFrom")
                        .value(today.minusDays(2).toString()));

        mvc.perform(get("/api/v1/me/nutrition-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/me/nutrition-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nutrientValues[0].targetAmount")
                        .value(1900.00));
    }

    @Test
    void nutritionReferenceUsesStableCodesAndNoIdentifiers()
            throws Exception {

        UserAccount account = createActiveUser();
        String token = loginAndGetAccessToken(account);

        mvc.perform(get("/api/v1/reference/nutrients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ENERGY"))
                .andExpect(jsonPath("$[0].displayName").value("Energy"))
                .andExpect(jsonPath("$[0].nutrientKind").value("ENERGY"))
                .andExpect(jsonPath("$[0].core").value(true))
                .andExpect(jsonPath("$[0].displayOrder").value(10))
                .andExpect(jsonPath("$[0].unitCode").value("kcal"))
                .andExpect(jsonPath("$[0].unitDisplayName")
                        .value("kilocalorie"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].nutrientId").doesNotExist())
                .andExpect(jsonPath("$[0].unitId").doesNotExist());
    }

    @Test
    void nutritionDomainFailuresUseSpecificSafeProblemDetails()
            throws Exception {

        UserAccount profileMissing = createActiveUser();
        String profileMissingToken = loginAndGetAccessToken(profileMissing);
        LocalDate effectiveFrom = todayUtc();

        mvc.perform(post("/api/v1/me/nutrition-targets/calculate")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(profileMissingToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(effectiveDateRequest(effectiveFrom)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isString());

        UserAccount weightChangeAccount = createActiveUser();
        String weightChangeToken = loginAndGetAccessToken(weightChangeAccount);
        prepareCalculationContext(weightChangeToken, effectiveFrom, "LOSE_WEIGHT");

        mvc.perform(post("/api/v1/me/nutrition-targets/calculated")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(weightChangeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(effectiveDateRequest(effectiveFrom)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value("GOAL_ADJUSTMENT_NOT_SUPPORTED"));

        assertThat(targets.findByUserIdAndEffectiveFrom(
                weightChangeAccount.internalId(),
                effectiveFrom)).isEmpty();
    }

    private void prepareCalculationContext(
            String token,
            LocalDate effectiveFrom,
            String nutritionGoal)
            throws Exception {

        UpdateProfileRequest profile = new UpdateProfileRequest(
                effectiveFrom.minusYears(30),
                Sex.MALE,
                new BigDecimal("180.00"),
                "MODERATE",
                nutritionGoal,
                null,
                null,
                1,
                null,
                null,
                null);

        mvc.perform(put("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profile)))
                .andExpect(status().isOk());

        RecordMeasurementRequest measurement = new RecordMeasurementRequest(
                effectiveFrom,
                new BigDecimal("80.00"),
                null,
                null,
                MeasurementSource.USER_ENTERED,
                null);

        mvc.perform(post("/api/v1/me/measurements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(measurement)))
                .andExpect(status().isCreated());
    }

    private void createUserDefinedTarget(
            String token,
            LocalDate effectiveFrom,
            String energy)
            throws Exception {

        mvc.perform(post("/api/v1/me/nutrition-targets/user-defined")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userDefinedRequest(effectiveFrom, energy)))
                .andExpect(status().isCreated());
    }

    private String effectiveDateRequest(
            LocalDate effectiveFrom)
            throws Exception {

        return objectMapper.writeValueAsString(
                new NutritionEffectiveDateRequest(effectiveFrom));
    }

    private String userDefinedRequest(
            LocalDate effectiveFrom,
            String energy)
            throws Exception {

        return objectMapper.writeValueAsString(
                new UserDefinedNutritionTargetRequest(
                        effectiveFrom,
                        List.of(new UserDefinedNutritionValueRequest(
                                "ENERGY",
                                new BigDecimal(energy),
                                null,
                                null,
                                false))));
    }

    private UserAccount createActiveUser() {

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");
        UserAccount account = accounts.saveAndFlush(
                new UserAccount(
                        "nutrition-api-" + suffix + "@example.com",
                        passwordEncoder.encode(RAW_PASSWORD),
                        "Nutrition API Test User"));
        account.verifyEmail(LocalDateTime.now(ZoneOffset.UTC));
        account = accounts.saveAndFlush(account);

        Role role = roles.findByCode(ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER reference data missing"));
        userRoles.saveAndFlush(new UserRole(account, role, null));
        return account;
    }

    private String loginAndGetAccessToken(
            UserAccount account)
            throws Exception {

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login/android")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(account.email(), RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(
                loginResult.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String loginBody(
            String email,
            String password) {

        return "{\"email\":\"" + email
                + "\",\"password\":\"" + password
                + "\"}";
    }

    private static String bearer(
            String token) {

        return "Bearer " + token;
    }
}
