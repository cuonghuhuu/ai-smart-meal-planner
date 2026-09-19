package com.smartmealplanner.pantry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.pantry.web.AdjustPantryItemRequest;
import com.smartmealplanner.pantry.web.ConsumePantryItemRequest;
import com.smartmealplanner.pantry.web.CreatePantryItemRequest;
import com.smartmealplanner.pantry.web.UpdatePantryItemRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL coverage for owner-scoped pantry lots and their quantity ledger. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class PantryIT {

    private static final String EMAIL_PREFIX = "p10-pantry-it-";
    private static final String INGREDIENT_PREFIX = "P10_PANTRY_INGREDIENT_";
    private static final String FOOD_PREFIX = "P10_PANTRY_FOOD_";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.11")
            .withDatabaseName("p10_pantry")
            .withUsername("p10_pantry_test")
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

    @Autowired
    PantryAvailabilityQueryService availability;

    @BeforeEach
    void cleanSyntheticRows() {
        jdbc.update("""
                delete from pantry_item_events
                where pantry_item_id in (
                    select id from pantry_items
                    where user_id in (
                        select id from users where email like ?))
                """, EMAIL_PREFIX + "%");
        jdbc.update("""
                delete from pantry_items
                where user_id in (
                    select id from users where email like ?)
                """, EMAIL_PREFIX + "%");
        jdbc.update("delete from ingredients where code like ?",
                INGREDIENT_PREFIX + "%");
        jdbc.update("delete from foods where code like ?", FOOD_PREFIX + "%");
        jdbc.update("delete from users where email like ?", EMAIL_PREFIX + "%");
    }

    @Test
    void requiresAuthenticationAndCreatesAnOwnerScopedLotWithAddedEvent()
            throws Exception {
        Fixture fixture = insertFixture();

        mvc.perform(get("/api/v1/me/pantry"))
                .andExpect(status().isUnauthorized());

        JsonNode created = create(fixture, new BigDecimal("500.0000"), null);
        UUID pantryPublicId = UUID.fromString(created.get("publicId").asText());

        assertThat(created.get("ingredientPublicId").asText())
                .isEqualTo(fixture.ingredientPublicId().toString());
        assertThat(created.get("quantityInitial").decimalValue())
                .isEqualByComparingTo("500.0000");
        assertThat(created.get("quantityRemaining").decimalValue())
                .isEqualByComparingTo("500.0000");
        assertThat(created.get("expiryKind").asText()).isEqualTo("UNKNOWN");
        assertThat(created.get("expiryConfidence").asText())
                .isEqualTo("UNKNOWN");
        assertThat(created.get("status").asText()).isEqualTo("AVAILABLE");
        assertThat(created.get("id")).isNull();
        assertThat(created.get("userId")).isNull();
        assertThat(created.get("ingredientId")).isNull();
        assertThat(created.get("foodId")).isNull();
        assertThat(created.get("unitId")).isNull();

        assertThat(jdbc.queryForObject("""
                select count(*)
                from pantry_item_events event
                join pantry_items item on item.id = event.pantry_item_id
                where item.public_id = unhex(replace(?, '-', ''))
                  and event.event_type = 'ADDED'
                """, Integer.class, pantryPublicId.toString()))
                .isEqualTo(1);
    }

    @Test
    void quantityOperationsUpdateTheLotAndAppendEventsAtomically()
            throws Exception {
        Fixture fixture = insertFixture();
        JsonNode created = create(fixture, new BigDecimal("500.0000"), null);
        UUID pantryPublicId = UUID.fromString(created.get("publicId").asText());

        mvc.perform(post("/api/v1/me/pantry/{publicId}/adjust", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdjustPantryItemRequest(
                                        new BigDecimal("-25.0000"),
                                        "measured"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityRemaining").value(475.0))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mvc.perform(post("/api/v1/me/pantry/{publicId}/consume", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConsumePantryItemRequest(
                                        new BigDecimal("100.0000"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityRemaining").value(375.0))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mvc.perform(post("/api/v1/me/pantry/{publicId}/discard", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"waste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityRemaining").value(0.0))
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        assertThat(jdbc.queryForObject("""
                select count(*) from pantry_item_events event
                join pantry_items item on item.id = event.pantry_item_id
                where item.public_id = unhex(replace(?, '-', ''))
                """, Integer.class, pantryPublicId.toString()))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                select quantity_delta from pantry_item_events event
                join pantry_items item on item.id = event.pantry_item_id
                where item.public_id = unhex(replace(?, '-', ''))
                  and event.event_type = 'DISCARDED'
                """, BigDecimal.class, pantryPublicId.toString()))
                .isEqualByComparingTo("-375.0000");

        mvc.perform(get("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        mvc.perform(get("/api/v1/me/pantry")
                        .param("includeClosed", "true")
                        .with(auth(fixture.userPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("DISCARDED"));

        mvc.perform(post("/api/v1/me/pantry/{publicId}/consume", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConsumePantryItemRequest(
                                        new BigDecimal("1.0000"), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_OPEN"));
    }

    @Test
    void knownExpiryPrecedesUnknownExpiryAndOtherUsersSeeNotFound()
            throws Exception {
        Fixture fixture = insertFixture();
        UUID knownId = UUID.fromString(create(
                fixture,
                new BigDecimal("1.0000"),
                new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        null,
                        new BigDecimal("1.0000"),
                        "g",
                        PantryStorageLocation.FRIDGE,
                        LocalDate.of(2026, 9, 19),
                        LocalDate.of(2026, 9, 22),
                        PantryExpiryKind.USE_BY,
                        PantryExpiryConfidence.LABELLED,
                        null)).get("publicId").asText());
        create(fixture, new BigDecimal("2.0000"), null);

        mvc.perform(get("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId")
                        .value(knownId.toString()))
                .andExpect(jsonPath("$[1].expiryDate")
                        .value(org.hamcrest.Matchers.nullValue()));

        UUID otherUser = insertAccount();
        mvc.perform(get("/api/v1/me/pantry/{publicId}", knownId)
                        .with(auth(otherUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("PANTRY_ITEM_NOT_FOUND"));
        mvc.perform(post("/api/v1/me/pantry/{publicId}/adjust", knownId)
                        .with(auth(otherUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdjustPantryItemRequest(
                                        new BigDecimal("1.0000"), null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("PANTRY_ITEM_NOT_FOUND"));
    }

    @Test
    void reservedStockIsExcludedFromP11Availability() throws Exception {
        Fixture fixture = insertFixture();
        JsonNode created = create(fixture, new BigDecimal("100.0000"), null);
        UUID pantryPublicId = UUID.fromString(created.get("publicId").asText());

        jdbc.update("""
                update pantry_items
                set status = 'RESERVED'
                where public_id = unhex(replace(?, '-', ''))
                """, pantryPublicId.toString());

        List<PantryAvailabilitySnapshot> snapshots = availability.availableFor(
                fixture.userPublicId());
        assertThat(snapshots).isEmpty();
    }

    @Test
    void metadataPutDoesNotChangeQuantityOrCreateAQuantityEvent()
            throws Exception {
        Fixture fixture = insertFixture();
        JsonNode created = create(fixture, new BigDecimal("80.0000"), null);
        UUID pantryPublicId = UUID.fromString(created.get("publicId").asText());
        UpdatePantryItemRequest request = new UpdatePantryItemRequest(
                PantryStorageLocation.FREEZER,
                LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 10, 1),
                PantryExpiryKind.BEST_BEFORE,
                PantryExpiryConfidence.ESTIMATED,
                "updated metadata");

        mvc.perform(put("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityInitial").value(80.0))
                .andExpect(jsonPath("$.quantityRemaining").value(80.0))
                .andExpect(jsonPath("$.storageLocation").value("FREEZER"))
                .andExpect(jsonPath("$.expiryKind").value("BEST_BEFORE"))
                .andExpect(jsonPath("$.note").value("updated metadata"));

        assertThat(jdbc.queryForObject("""
                select count(*) from pantry_item_events event
                join pantry_items item on item.id = event.pantry_item_id
                where item.public_id = unhex(replace(?, '-', ''))
                """, Integer.class, pantryPublicId.toString()))
                .isEqualTo(1);
    }

    @Test
    void validatesUnknownUnitAndDatedExpiryContract() throws Exception {
        Fixture fixture = insertFixture();
        CreatePantryItemRequest invalidUnit = new CreatePantryItemRequest(
                fixture.ingredientPublicId(),
                null,
                new BigDecimal("1.0000"),
                "not-a-unit",
                PantryStorageLocation.PANTRY,
                null,
                null,
                null,
                null,
                null);

        mvc.perform(post("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUnit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_NOT_FOUND"));

        UUID unrelatedFood = insertUnmappedFood();
        CreatePantryItemRequest unrelatedFoodRequest = new CreatePantryItemRequest(
                fixture.ingredientPublicId(),
                unrelatedFood,
                new BigDecimal("1.0000"),
                "g",
                PantryStorageLocation.PANTRY,
                null,
                null,
                null,
                null,
                null);
        mvc.perform(post("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                unrelatedFoodRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_MAPPED"));

        CreatePantryItemRequest missingExpiryMetadata = new CreatePantryItemRequest(
                fixture.ingredientPublicId(),
                null,
                new BigDecimal("1.0000"),
                "g",
                PantryStorageLocation.PANTRY,
                LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 22),
                null,
                null,
                null);
        mvc.perform(post("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                missingExpiryMetadata)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private JsonNode create(
            Fixture fixture,
            BigDecimal quantity,
            CreatePantryItemRequest explicitRequest) throws Exception {

        CreatePantryItemRequest request = explicitRequest == null
                ? new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        null,
                        quantity,
                        "g",
                        PantryStorageLocation.FRIDGE,
                        null,
                        null,
                        null,
                        null,
                        null)
                : explicitRequest;
        MvcResult result = mvc.perform(post("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private RequestPostProcessor auth(UUID publicId) {
        return jwt().jwt(jwt -> jwt.subject(publicId.toString()));
    }

    private Fixture insertFixture() {
        UUID userPublicId = insertAccount();
        UUID ingredientPublicId = UUID.randomUUID();
        Long gramUnitId = jdbc.queryForObject(
                "select id from measurement_units where code = 'g'", Long.class);
        String ingredientCode = INGREDIENT_PREFIX + ingredientPublicId;
        jdbc.update("""
                insert into ingredients
                    (public_id, code, display_name, default_unit_id,
                     is_staple, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P10 pantry ingredient',
                        ?, false, true, 0)
                """, ingredientPublicId.toString(), ingredientCode, gramUnitId);
        return new Fixture(userPublicId, ingredientPublicId);
    }

    private UUID insertAccount() {
        UUID publicId = UUID.randomUUID();
        String email = EMAIL_PREFIX + publicId + "@example.test";
        jdbc.update("""
                insert into users
                    (public_id, email, password_hash, display_name,
                     account_status, email_verified_at, time_zone, locale, version)
                values (unhex(replace(?, '-', '')), ?, 'test-hash',
                        'P10 Pantry User', 'ACTIVE', ?, 'UTC', 'vi', 0)
                """, publicId.toString(), email,
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 9, 19, 0, 0)));
        return publicId;
    }

    private UUID insertUnmappedFood() {
        UUID publicId = UUID.randomUUID();
        String code = FOOD_PREFIX + publicId;
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, nutrition_basis, source,
                     source_reference, revision, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P10 unrelated food',
                        'PER_100_G', 'CURATED', 'P10 pantry test', 1, true, 0)
                """, publicId.toString(), code);
        return publicId;
    }

    private record Fixture(UUID userPublicId, UUID ingredientPublicId) {
    }
}
