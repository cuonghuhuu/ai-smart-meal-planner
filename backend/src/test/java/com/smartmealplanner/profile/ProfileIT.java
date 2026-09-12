package com.smartmealplanner.profile;

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
import com.smartmealplanner.profile.persistence.MeasurementSource;
import com.smartmealplanner.profile.persistence.ReactionKind;
import com.smartmealplanner.profile.persistence.Sex;
import com.smartmealplanner.profile.web.RecordMeasurementRequest;
import com.smartmealplanner.profile.web.ReplaceAllergensRequest;
import com.smartmealplanner.profile.web.ReplaceDietaryPreferencesRequest;
import com.smartmealplanner.profile.web.UpdateMeasurementRequest;
import com.smartmealplanner.profile.web.UpdateProfileRequest;
import com.smartmealplanner.profile.web.UserAllergenItemRequest;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class ProfileIT {

    private static final String RAW_PASSWORD =
            "correct-horse-battery-staple";

    private static final String ROLE_USER =
            "ROLE_USER";

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.11")
                    .withDatabaseName("smart_meal_planner")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(
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

    // =========================================================================
    // USER PROFILE TESTS
    // =========================================================================

    @Test
    void unauthenticatedProfileEndpointsReturn401()
            throws Exception {

        mvc.perform(
                        get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer("invalid.token"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getProfileWhenAbsentReturns404()
            throws Exception {

        UserAccount user = createActiveUser();
        String token = loginAndGetAccessToken(user);

        mvc.perform(
                        get("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createAndUpdateProfileLifeCycleAndOptimisticLocking()
            throws Exception {

        UserAccount user = createActiveUser();
        String token = loginAndGetAccessToken(user);

        UpdateProfileRequest createRequest = new UpdateProfileRequest(
                LocalDate.of(1995, 6, 15),
                Sex.FEMALE,
                BigDecimal.valueOf(165.5),
                "MODERATE",
                "LOSE_WEIGHT",
                BigDecimal.valueOf(58.0),
                BigDecimal.valueOf(-0.5),
                2,
                45,
                "Mediterranean taste",
                null);

        MvcResult createResult = mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1995-06-15"))
                .andExpect(jsonPath("$.sex").value("FEMALE"))
                .andExpect(jsonPath("$.heightCm").value(165.5))
                .andExpect(jsonPath("$.activityLevel").value("MODERATE"))
                .andExpect(jsonPath("$.nutritionGoal").value("LOSE_WEIGHT"))
                .andExpect(jsonPath("$.targetWeightKg").value(58.0))
                .andExpect(jsonPath("$.weeklyChangeKg").value(-0.5))
                .andExpect(jsonPath("$.householdSize").value(2))
                .andExpect(jsonPath("$.maxCookMinutes").value(45))
                .andExpect(jsonPath("$.notes").value("Mediterranean taste"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long version = createdJson.get("version").asLong();
        assertThat(version).isEqualTo(0L);

        // GET profile returns the created profile
        mvc.perform(
                        get("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1995-06-15"))
                .andExpect(jsonPath("$.activityLevel").value("MODERATE"))
                .andExpect(jsonPath("$.version").value(0));

        // Update profile with matching version
        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                LocalDate.of(1995, 6, 15),
                Sex.FEMALE,
                BigDecimal.valueOf(165.5),
                "LIGHT",
                "MAINTAIN",
                BigDecimal.valueOf(55.0),
                BigDecimal.ZERO,
                1,
                30,
                "Updated notes",
                version);

        MvcResult updateResult = mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityLevel").value("LIGHT"))
                .andExpect(jsonPath("$.nutritionGoal").value("MAINTAIN"))
                .andExpect(jsonPath("$.notes").value("Updated notes"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();

        // Optimistic locking conflict: update with old version (0)
        UpdateProfileRequest staleRequest = new UpdateProfileRequest(
                LocalDate.of(1995, 6, 15),
                Sex.FEMALE,
                BigDecimal.valueOf(165.5),
                "LIGHT",
                "MAINTAIN",
                BigDecimal.valueOf(55.0),
                BigDecimal.ZERO,
                1,
                30,
                "Stale update",
                0L);

        mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(staleRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void profileValidationRejectsInvalidConstraints()
            throws Exception {

        UserAccount user = createActiveUser();
        String token = loginAndGetAccessToken(user);

        // Height out of bounds
        UpdateProfileRequest badHeight = new UpdateProfileRequest(
                null, null, BigDecimal.valueOf(15), null, null, null, null, null, null, null, null);
        mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badHeight)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        // Unknown activity level code
        UpdateProfileRequest badActivity = new UpdateProfileRequest(
                null, null, BigDecimal.valueOf(170), "EXTREME_SUPER", null, null, null, null, null, null, null);
        mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badActivity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        // Unknown nutrition goal code
        UpdateProfileRequest badGoal = new UpdateProfileRequest(
                null, null, BigDecimal.valueOf(170), null, "FLY_TO_MARS", null, null, null, null, null, null);
        mvc.perform(
                        put("/api/v1/me/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badGoal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // =========================================================================
    // BODY MEASUREMENTS TESTS
    // =========================================================================

    @Test
    void unauthenticatedMeasurementsEndpointsReturn401()
            throws Exception {

        mvc.perform(
                        get("/api/v1/me/measurements/latest"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        get("/api/v1/me/measurements"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer("invalid.token"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bodyMeasurementsFlowAndSameDateCorrection()
            throws Exception {

        UserAccount userA = createActiveUser();
        String tokenA = loginAndGetAccessToken(userA);

        UserAccount userB = createActiveUser();
        String tokenB = loginAndGetAccessToken(userB);

        // User A has no measurements initially
        mvc.perform(
                        get("/api/v1/me/measurements/latest")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // User A records day 1 measurement
        LocalDate day1 = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        RecordMeasurementRequest m1 = new RecordMeasurementRequest(
                day1,
                BigDecimal.valueOf(75.5),
                BigDecimal.valueOf(20.0),
                BigDecimal.valueOf(85.0),
                null,
                "First measurement");

        mvc.perform(
                        post("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(m1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measuredOn").value(day1.toString()))
                .andExpect(jsonPath("$.weightKg").value(75.5))
                .andExpect(jsonPath("$.source").value("USER_ENTERED"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());

        // Latest is day 1
        mvc.perform(
                        get("/api/v1/me/measurements/latest")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredOn").value(day1.toString()))
                .andExpect(jsonPath("$.weightKg").value(75.5));

        // User A records day 2 measurement
        LocalDate day2 = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        RecordMeasurementRequest m2 = new RecordMeasurementRequest(
                day2,
                BigDecimal.valueOf(75.0),
                BigDecimal.valueOf(19.8),
                BigDecimal.valueOf(84.5),
                null,
                "Second day weigh-in");

        mvc.perform(
                        post("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(m2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measuredOn").value(day2.toString()))
                .andExpect(jsonPath("$.weightKg").value(75.0))
                .andExpect(jsonPath("$.source").value("USER_ENTERED"));

        // Latest is now day 2
        mvc.perform(
                        get("/api/v1/me/measurements/latest")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measuredOn").value(day2.toString()))
                .andExpect(jsonPath("$.weightKg").value(75.0));

        // Same-day correction via POST on day 2
        RecordMeasurementRequest correctionDay2 = new RecordMeasurementRequest(
                day2,
                BigDecimal.valueOf(74.8),
                BigDecimal.valueOf(19.5),
                BigDecimal.valueOf(84.0),
                null,
                "Corrected scale reading");

        mvc.perform(
                        post("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(correctionDay2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measuredOn").value(day2.toString()))
                .andExpect(jsonPath("$.weightKg").value(74.8))
                .andExpect(jsonPath("$.source").value("CORRECTED"))
                .andExpect(jsonPath("$.note").value("Corrected scale reading"));

        // Verify history has exactly 2 records, duplicate prevented
        mvc.perform(
                        get("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].measuredOn").value(day2.toString()))
                .andExpect(jsonPath("$[0].weightKg").value(74.8))
                .andExpect(jsonPath("$[0].source").value("CORRECTED"))
                .andExpect(jsonPath("$[1].measuredOn").value(day1.toString()))
                .andExpect(jsonPath("$[1].weightKg").value(75.5));

        // PUT update on day 1
        UpdateMeasurementRequest putCorrection = new UpdateMeasurementRequest(
                BigDecimal.valueOf(75.2),
                null,
                null,
                null,
                "Day 1 correction via PUT");

        mvc.perform(
                        put("/api/v1/me/measurements/" + day1)
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(putCorrection)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(75.2))
                .andExpect(jsonPath("$.source").value("CORRECTED"));

        // OWNERSHIP ISOLATION: User B sees zero measurements!
        mvc.perform(
                        get("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(
                        get("/api/v1/me/measurements/latest")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void measurementValidationRejectsFutureDateAndBadWeight()
            throws Exception {

        UserAccount user = createActiveUser();
        String token = loginAndGetAccessToken(user);

        // Future date
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        RecordMeasurementRequest future = new RecordMeasurementRequest(
                tomorrow, BigDecimal.valueOf(70.0), null, null, null, null);

        mvc.perform(
                        post("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        // Negative weight
        RecordMeasurementRequest negWeight = new RecordMeasurementRequest(
                LocalDate.now(ZoneOffset.UTC), BigDecimal.valueOf(-5.0), null, null, null, null);

        mvc.perform(
                        post("/api/v1/me/measurements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(negWeight)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // =========================================================================
    // DIETARY PREFERENCES & ALLERGENS TESTS
    // =========================================================================

    @Test
    void dietaryPreferencesFullFlowAndOwnership()
            throws Exception {

        UserAccount userA = createActiveUser();
        String tokenA = loginAndGetAccessToken(userA);

        UserAccount userB = createActiveUser();
        String tokenB = loginAndGetAccessToken(userB);

        // Initial empty
        mvc.perform(
                        get("/api/v1/me/dietary-preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Replace with preferences and duplicate handling
        ReplaceDietaryPreferencesRequest replaceReq = new ReplaceDietaryPreferencesRequest(
                List.of("VEGETARIAN", "LOW_SODIUM", "VEGETARIAN"));

        mvc.perform(
                        put("/api/v1/me/dietary-preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(replaceReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("VEGETARIAN"))
                .andExpect(jsonPath("$[0].isExclusionary").value(true))
                .andExpect(jsonPath("$[1].code").value("LOW_SODIUM"))
                .andExpect(jsonPath("$[1].isExclusionary").value(false));

        // GET returns updated preferences
        mvc.perform(
                        get("/api/v1/me/dietary-preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // User B has none
        mvc.perform(
                        get("/api/v1/me/dietary-preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Reject invalid preference code
        ReplaceDietaryPreferencesRequest badReq = new ReplaceDietaryPreferencesRequest(
                List.of("NOT_A_DIET"));
        mvc.perform(
                        put("/api/v1/me/dietary-preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void allergensFullFlowAndSafetySemantics()
            throws Exception {

        UserAccount userA = createActiveUser();
        String tokenA = loginAndGetAccessToken(userA);

        UserAccount userB = createActiveUser();
        String tokenB = loginAndGetAccessToken(userB);

        // Initial empty
        mvc.perform(
                        get("/api/v1/me/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Add declared allergens with reaction kind
        ReplaceAllergensRequest request = new ReplaceAllergensRequest(
                List.of(
                        new UserAllergenItemRequest("PEANUT", ReactionKind.ALLERGY, "Carries EpiPen"),
                        new UserAllergenItemRequest("MILK", ReactionKind.INTOLERANCE, "Lactose discomfort")));

        mvc.perform(
                        put("/api/v1/me/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].allergen").value("PEANUT"))
                .andExpect(jsonPath("$[0].reactionKind").value("ALLERGY"))
                .andExpect(jsonPath("$[0].note").value("Carries EpiPen"))
                .andExpect(jsonPath("$[1].allergen").value("MILK"))
                .andExpect(jsonPath("$[1].reactionKind").value("INTOLERANCE"));

        // GET allergens
        mvc.perform(
                        get("/api/v1/me/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // User B has none
        mvc.perform(
                        get("/api/v1/me/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Reject duplicates in same request
        ReplaceAllergensRequest dupReq = new ReplaceAllergensRequest(
                List.of(
                        new UserAllergenItemRequest("EGG", ReactionKind.ALLERGY, null),
                        new UserAllergenItemRequest("EGG", ReactionKind.INTOLERANCE, null)));

        mvc.perform(
                        put("/api/v1/me/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dupReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        // Reject unknown allergen code
        ReplaceAllergensRequest unknownReq = new ReplaceAllergensRequest(
                List.of(new UserAllergenItemRequest("MAGIC_DUST", ReactionKind.ALLERGY, null)));

        mvc.perform(
                        put("/api/v1/me/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // =========================================================================
    // REFERENCE DATA TESTS
    // =========================================================================

    @Test
    void unauthenticatedReferenceDataReturns401()
            throws Exception {

        mvc.perform(
                        get("/api/v1/reference/activity-levels"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        get("/api/v1/reference/nutrition-goals"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        get("/api/v1/reference/dietary-preferences"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        get("/api/v1/reference/allergens"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedReferenceDataReturnsDeterministicOrderWithoutSurrogateIds()
            throws Exception {

        UserAccount user = createActiveUser();
        String token = loginAndGetAccessToken(user);

        // Activity levels
        mvc.perform(
                        get("/api/v1/reference/activity-levels")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SEDENTARY"))
                .andExpect(jsonPath("$[0].energyFactor").value(1.2))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[1].code").value("LIGHT"));

        // Nutrition goals
        mvc.perform(
                        get("/api/v1/reference/nutrition-goals")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LOSE_WEIGHT"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[1].code").value("MAINTAIN"));

        // Dietary preferences
        mvc.perform(
                        get("/api/v1/reference/dietary-preferences")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("VEGETARIAN"))
                .andExpect(jsonPath("$[0].isExclusionary").value(true))
                .andExpect(jsonPath("$[0].id").doesNotExist());

        // Allergens
        mvc.perform(
                        get("/api/v1/reference/allergens")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("GLUTEN"))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }

    // =========================================================================
    // TEST HELPERS
    // =========================================================================

    private UserAccount createActiveUser() {

        String suffix =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        UserAccount account =
                accounts.saveAndFlush(
                        new UserAccount(
                                "profile-" + suffix + "@example.com",
                                passwordEncoder.encode(RAW_PASSWORD),
                                "Profile Test User"));

        account.verifyEmail(
                LocalDateTime.now(ZoneOffset.UTC));

        account = accounts.saveAndFlush(account);

        Role role =
                roles.findByCode(ROLE_USER)
                        .orElseThrow(() -> new IllegalStateException("ROLE_USER reference data missing"));

        userRoles.saveAndFlush(
                new UserRole(account, role, null));

        return account;
    }

    private String loginAndGetAccessToken(UserAccount account)
            throws Exception {

        MvcResult loginResult =
                mvc.perform(
                                post("/api/v1/auth/login/android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginBody(account.email(), RAW_PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn();

        JsonNode body =
                objectMapper.readTree(
                        loginResult.getResponse().getContentAsString());

        return body.get("accessToken").asText();
    }

    private static String loginBody(
            String email,
            String password) {

        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String bearer(
            String token) {

        return "Bearer " + token;
    }
}
