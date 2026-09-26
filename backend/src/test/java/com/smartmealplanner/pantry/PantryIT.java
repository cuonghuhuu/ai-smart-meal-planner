package com.smartmealplanner.pantry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.pantry.web.AdjustPantryItemRequest;
import com.smartmealplanner.pantry.web.ConsumePantryItemRequest;
import com.smartmealplanner.pantry.web.CreatePantryItemRequest;
import com.smartmealplanner.pantry.web.UpdatePantryItemRequest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;

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

    @Autowired
    EntityManagerFactory entityManagerFactory;

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
        assertThat(created.get("expiryDate").isNull()).isTrue();
        assertThat(created.get("foodPublicId").isNull()).isTrue();
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
        assertThat(jdbc.queryForObject("""
                select item.user_id
                from pantry_items item
                where item.public_id = unhex(replace(?, '-', ''))
                """, Long.class, pantryPublicId.toString()))
                .isEqualTo(userId(fixture.userPublicId()));
    }

    @Test
    void createsLotsWithEverySupportedCanonicalUnit() throws Exception {
        Fixture fixture = insertFixture();
        Map<String, String> displayNames = Map.of(
                "g", "gram",
                "kg", "kilogram",
                "ml", "millilitre",
                "piece", "piece");

        for (String unitCode : List.of("g", "kg", "ml", "piece")) {
            JsonNode created = create(
                    fixture,
                    BigDecimal.ONE,
                    new CreatePantryItemRequest(
                            fixture.ingredientPublicId(),
                            null,
                            BigDecimal.ONE,
                            unitCode,
                            PantryStorageLocation.PANTRY,
                            null,
                            null,
                            null,
                            null,
                            null));
            UUID pantryPublicId = UUID.fromString(
                    created.get("publicId").asText());

            assertThat(created.get("unitCode").asText()).isEqualTo(unitCode);
            assertThat(created.get("unitDisplayName").asText())
                    .isEqualTo(displayNames.get(unitCode));
            assertThat(unitCodeFor(pantryPublicId)).isEqualTo(unitCode);
        }
    }

    @Test
    void rejectsMissingStorageLocationAsClientValidation() throws Exception {
        Fixture fixture = insertFixture();

        mvc.perform(post("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePantryItemRequest(
                                        fixture.ingredientPublicId(),
                                        null,
                                        BigDecimal.ONE,
                                        "piece",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createsEggLotWithPieceAndUnknownExpiryWithoutShelfLifeInference()
            throws Exception {
        Fixture fixture = insertFixture("Trứng gà", "piece", (short) 14);

        JsonNode created = create(
                fixture,
                new BigDecimal("5"),
                new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        null,
                        new BigDecimal("5"),
                        "piece",
                        PantryStorageLocation.FRIDGE,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(created.get("ingredientName").asText()).isEqualTo("Trứng gà");
        assertThat(created.get("quantityInitial").decimalValue())
                .isEqualByComparingTo("5");
        assertThat(created.get("quantityRemaining").decimalValue())
                .isEqualByComparingTo("5");
        assertThat(created.get("unitCode").asText()).isEqualTo("piece");
        assertThat(created.get("storageLocation").asText()).isEqualTo("FRIDGE");
        assertThat(created.get("expiryDate").isNull()).isTrue();
        assertThat(created.get("expiryKind").asText()).isEqualTo("UNKNOWN");
        assertThat(created.get("expiryConfidence").asText())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void quantityOperationsUpdateTheLotAndAppendEventsAtomically()
            throws Exception {
        Fixture fixture = insertFixture();
        JsonNode created = create(fixture, new BigDecimal("500.0000"), null);
        UUID pantryPublicId = UUID.fromString(created.get("publicId").asText());

        mvc.perform(post("/api/v1/me/pantry/{publicId}/consume", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ConsumePantryItemRequest(
                                        new BigDecimal("500.0001"), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(1);
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo("500.0000");

        mvc.perform(post("/api/v1/me/pantry/{publicId}/adjust", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdjustPantryItemRequest(
                                        new BigDecimal("-1.0000"),
                                        "x".repeat(256)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(1);
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo("500.0000");

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

        List<PantryEventRow> ledger = eventsFor(pantryPublicId);
        assertThat(ledger).hasSize(4);
        assertThat(ledger).extracting(PantryEventRow::eventType)
                .containsExactly(
                        PantryItemEventType.ADDED,
                        PantryItemEventType.ADJUSTED,
                        PantryItemEventType.CONSUMED,
                        PantryItemEventType.DISCARDED);
        assertThat(ledger).extracting(PantryEventRow::quantityDelta)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(
                        new BigDecimal("500.0000"),
                        new BigDecimal("-25.0000"),
                        new BigDecimal("-100.0000"),
                        new BigDecimal("-375.0000"));
        assertThat(ledger).extracting(PantryEventRow::quantityAfter)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(
                        new BigDecimal("500.0000"),
                        new BigDecimal("475.0000"),
                        new BigDecimal("375.0000"),
                        BigDecimal.ZERO);
        assertThat(ledger).allSatisfy(event -> {
            assertThat(event.ownerId()).isEqualTo(userId(fixture.userPublicId()));
            assertThat(event.pantryItemId()).isEqualTo(lotId(pantryPublicId));
            assertMicrosecondPrecision(event.occurredAt());
        });
        LocalDateTime closedAt = closedAtFor(pantryPublicId);
        assertThat(closedAt).isEqualTo(ledger.getLast().occurredAt());
        assertMicrosecondPrecision(closedAt);

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
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(4);
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(itemCountFor(pantryPublicId)).isEqualTo(1);
    }

    @Test
    void knownExpiryPrecedesUnknownExpiryAndInvalidChronologyIsRejected()
            throws Exception {
        Fixture fixture = insertFixture();
        JsonNode known = create(
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
                        null));
        UUID knownId = UUID.fromString(known.get("publicId").asText());
        JsonNode unknown = create(fixture, new BigDecimal("2.0000"), null);

        assertThat(known.get("expiryDate").asText()).isEqualTo("2026-09-22");
        assertThat(known.get("expiryKind").asText()).isEqualTo("USE_BY");
        assertThat(known.get("expiryConfidence").asText())
                .isEqualTo("LABELLED");
        java.sql.Date persistedExpiryDate = jdbc.queryForObject("""
                select expiry_date from pantry_items
                where public_id = unhex(replace(?, '-', ''))
                """, java.sql.Date.class, knownId.toString());
        assertThat(persistedExpiryDate.toLocalDate())
                .isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(unknown.get("expiryDate").isNull()).isTrue();
        assertThat(unknown.get("expiryKind").asText()).isEqualTo("UNKNOWN");
        assertThat(unknown.get("expiryConfidence").asText())
                .isEqualTo("UNKNOWN");

        mvc.perform(get("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId")
                        .value(knownId.toString()))
                .andExpect(jsonPath("$[1].expiryDate")
                        .value(org.hamcrest.Matchers.nullValue()));

        CreatePantryItemRequest expiryBeforeAcquisition =
                new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        null,
                        new BigDecimal("1.0000"),
                        "g",
                        PantryStorageLocation.FRIDGE,
                        LocalDate.of(2026, 9, 22),
                        LocalDate.of(2026, 9, 19),
                        PantryExpiryKind.USE_BY,
                        PantryExpiryConfidence.LABELLED,
                        null);
        mvc.perform(post("/api/v1/me/pantry")
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                expiryBeforeAcquisition)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void ownerIsolationAppliesToEveryLotReadAndMutation() throws Exception {
        Fixture owner = insertFixture();
        UUID pantryPublicId = UUID.fromString(create(
                owner, new BigDecimal("10.0000"), null).get("publicId").asText());
        UUID otherUser = insertAccount();

        mvc.perform(get("/api/v1/me/pantry")
                        .with(auth(otherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        mvc.perform(get("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(otherUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PANTRY_ITEM_NOT_FOUND"));
        mvc.perform(put("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(otherUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageLocation\":\"FREEZER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PANTRY_ITEM_NOT_FOUND"));
        mvc.perform(post("/api/v1/me/pantry/{publicId}/adjust", pantryPublicId)
                        .with(auth(otherUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantityDelta\":-1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PANTRY_ITEM_NOT_FOUND"));
        mvc.perform(post("/api/v1/me/pantry/{publicId}/consume", pantryPublicId)
                        .with(auth(otherUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PANTRY_ITEM_NOT_FOUND"));
        mvc.perform(post("/api/v1/me/pantry/{publicId}/discard", pantryPublicId)
                        .with(auth(otherUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"intrusion\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PANTRY_ITEM_NOT_FOUND"));

        assertThat(availability.availableFor(otherUser)).isEmpty();
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo("10.0000");
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(1);
    }

    @Test
    void reservedStockIsExcludedFromP11Availability() throws Exception {
        Fixture fixture = insertFixture();
        JsonNode created = create(fixture, new BigDecimal("100.0000"), null);
        UUID pantryPublicId = UUID.fromString(created.get("publicId").asText());
        int eventCountBeforeQuery = eventCountFor(pantryPublicId);

        jdbc.update("""
                update pantry_items
                set status = 'RESERVED'
                where public_id = unhex(replace(?, '-', ''))
                """, pantryPublicId.toString());

        List<PantryAvailabilitySnapshot> snapshots = availability.availableFor(
                fixture.userPublicId());
        assertThat(snapshots).isEmpty();
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(eventCountBeforeQuery);
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo("100.0000");
    }

    @Test
    void availabilityReturnsAvailableLotsSeparatelyWithoutMutatingPantry()
            throws Exception {
        Fixture fixture = insertFixture();
        UUID gramLotId = UUID.fromString(create(
                fixture,
                new BigDecimal("100.0000"),
                new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        null,
                        new BigDecimal("100.0000"),
                        "g",
                        PantryStorageLocation.PANTRY,
                        null,
                        null,
                        null,
                        null,
                        null)).get("publicId").asText());
        UUID kilogramLotId = UUID.fromString(create(
                fixture,
                new BigDecimal("1.0000"),
                new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        null,
                        new BigDecimal("1.0000"),
                        "kg",
                        PantryStorageLocation.PANTRY,
                        null,
                        null,
                        null,
                        null,
                        null)).get("publicId").asText());
        int eventsBeforeQuery = eventCountFor(gramLotId)
                + eventCountFor(kilogramLotId);

        List<PantryAvailabilitySnapshot> snapshots = availability.availableFor(
                fixture.userPublicId());

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).extracting(
                PantryAvailabilitySnapshot::pantryItemPublicId)
                .containsExactlyInAnyOrder(gramLotId, kilogramLotId);
        assertThat(snapshots).extracting(PantryAvailabilitySnapshot::unitCode)
                .containsExactlyInAnyOrder("g", "kg");
        assertThat(snapshots).extracting(
                PantryAvailabilitySnapshot::quantityRemaining)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(
                        new BigDecimal("100.0000"),
                        new BigDecimal("1.0000"));
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.ingredientPublicId())
                    .isEqualTo(fixture.ingredientPublicId());
            assertThat(snapshot.foodPublicId()).isNull();
        });
        assertThat(eventCountFor(gramLotId) + eventCountFor(kilogramLotId))
                .isEqualTo(eventsBeforeQuery);
        assertThat(quantityRemainingFor(gramLotId))
                .isEqualByComparingTo("100.0000");
        assertThat(quantityRemainingFor(kilogramLotId))
                .isEqualByComparingTo("1.0000");
        assertThat(availability.availableFor(insertAccount())).isEmpty();
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
                "updated metadata",
                null);

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

        mvc.perform(put("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageLocation\":\"FREEZER\",\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo("80.0000");
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(1);

        mvc.perform(put("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageLocation\":\"FREEZER\",\"quantity\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(quantityRemainingFor(pantryPublicId))
                .isEqualByComparingTo("80.0000");
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(1);
    }

    @Test
    void optionalMappedFoodPersistsWhileIngredientOnlyLotsRemainValid()
            throws Exception {
        Fixture fixture = insertFixture();
        JsonNode ingredientOnly = create(
                fixture,
                new BigDecimal("2.0000"),
                null);
        UUID mappedFoodPublicId = insertMappedFood(fixture);
        JsonNode withFood = create(
                fixture,
                new BigDecimal("3.0000"),
                new CreatePantryItemRequest(
                        fixture.ingredientPublicId(),
                        mappedFoodPublicId,
                        new BigDecimal("3.0000"),
                        "g",
                        PantryStorageLocation.FRIDGE,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(ingredientOnly.get("foodPublicId").isNull()).isTrue();
        assertThat(withFood.get("ingredientPublicId").asText())
                .isEqualTo(fixture.ingredientPublicId().toString());
        assertThat(withFood.get("foodPublicId").asText())
                .isEqualTo(mappedFoodPublicId.toString());
        assertThat(withFood.get("foodName").asText())
                .isEqualTo("P10 mapped food");
        assertThat(foodPublicIdFor(UUID.fromString(
                withFood.get("publicId").asText())))
                .isEqualTo(mappedFoodPublicId);
    }

    @Test
    void databaseManagedLotTimestampsRoundTripAtMicrosecondPrecision()
            throws Exception {
        Fixture fixture = insertFixture();
        UUID pantryPublicId = UUID.fromString(create(
                fixture, new BigDecimal("1.0000"), null).get("publicId").asText());

        LocalDateTime createdAt = itemTimestamp(pantryPublicId, "created_at");
        LocalDateTime initialUpdatedAt = itemTimestamp(
                pantryPublicId, "updated_at");
        assertMicrosecondPrecision(createdAt);
        assertMicrosecondPrecision(initialUpdatedAt);
        JsonNode persisted = getItem(fixture.userPublicId(), pantryPublicId);
        assertThat(parseTimestamp(persisted, "createdAt")).isEqualTo(createdAt);
        assertThat(parseTimestamp(persisted, "updatedAt"))
                .isEqualTo(initialUpdatedAt);

        UpdatePantryItemRequest metadata = new UpdatePantryItemRequest(
                PantryStorageLocation.FREEZER,
                null,
                null,
                PantryExpiryKind.UNKNOWN,
                PantryExpiryConfidence.UNKNOWN,
                "timestamp probe",
                null);
        mvc.perform(put("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(fixture.userPublicId()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(metadata)))
                .andExpect(status().isOk());

        LocalDateTime updatedAt = itemTimestamp(pantryPublicId, "updated_at");
        assertMicrosecondPrecision(updatedAt);
        JsonNode updated = getItem(fixture.userPublicId(), pantryPublicId);
        assertThat(parseTimestamp(updated, "updatedAt")).isEqualTo(updatedAt);
    }

    @Test
    void stalePantryLotViewCannotOverwriteCommittedMetadata() throws Exception {
        Fixture fixture = insertFixture();
        UUID pantryPublicId = UUID.fromString(create(
                fixture, new BigDecimal("10.0000"), null).get("publicId").asText());
        Long pantryItemId = lotId(pantryPublicId);
        long initialVersion = jdbc.queryForObject(
                "select version from pantry_items where id = ?",
                Long.class,
                pantryItemId);

        try (EntityManager first = entityManagerFactory.createEntityManager();
                EntityManager second = entityManagerFactory.createEntityManager()) {
            try {
                first.getTransaction().begin();
                second.getTransaction().begin();
                PantryItem current = first.find(PantryItem.class, pantryItemId);
                PantryItem stale = second.find(PantryItem.class, pantryItemId);
                assertThat(current.version()).isEqualTo(initialVersion);
                assertThat(stale.version()).isEqualTo(initialVersion);

                current.updateMetadata(
                        PantryStorageLocation.FREEZER,
                        null,
                        null,
                        PantryExpiryKind.UNKNOWN,
                        PantryExpiryConfidence.UNKNOWN,
                        "first writer");
                first.getTransaction().commit();

                stale.updateMetadata(
                        PantryStorageLocation.OTHER,
                        null,
                        null,
                        PantryExpiryKind.UNKNOWN,
                        PantryExpiryConfidence.UNKNOWN,
                        "stale writer");
                assertThatThrownBy(second::flush)
                        .isInstanceOf(OptimisticLockException.class);
            } finally {
                rollbackIfActive(first);
                rollbackIfActive(second);
            }
        }

        assertThat(jdbc.queryForObject(
                "select storage_location from pantry_items where id = ?",
                String.class,
                pantryItemId)).isEqualTo("FREEZER");
        assertThat(jdbc.queryForObject(
                "select version from pantry_items where id = ?",
                Long.class,
                pantryItemId)).isEqualTo(initialVersion + 1);
        assertThat(eventCountFor(pantryPublicId)).isEqualTo(1);
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
        return insertFixture("P10 pantry ingredient", "g", null);
    }

    private Fixture insertFixture(
            String ingredientName,
            String defaultUnitCode,
            Short typicalShelfLifeDays) {

        UUID userPublicId = insertAccount();
        UUID ingredientPublicId = UUID.randomUUID();
        Long defaultUnitId = jdbc.queryForObject(
                "select id from measurement_units where code = ?",
                Long.class,
                defaultUnitCode);
        String ingredientCode = INGREDIENT_PREFIX + ingredientPublicId;
        if (typicalShelfLifeDays == null) {
            jdbc.update("""
                    insert into ingredients
                        (public_id, code, display_name, default_unit_id,
                         is_staple, is_active, version)
                    values (unhex(replace(?, '-', '')), ?, ?, ?, false, true, 0)
                    """,
                    ingredientPublicId.toString(),
                    ingredientCode,
                    ingredientName,
                    defaultUnitId);
        } else {
            jdbc.update("""
                    insert into ingredients
                        (public_id, code, display_name, default_unit_id,
                         typical_shelf_life_days, is_staple, is_active, version)
                    values (unhex(replace(?, '-', '')), ?, ?, ?, ?, false, true, 0)
                    """,
                    ingredientPublicId.toString(),
                    ingredientCode,
                    ingredientName,
                    defaultUnitId,
                    typicalShelfLifeDays);
        }
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

    private UUID insertMappedFood(Fixture fixture) {
        UUID publicId = UUID.randomUUID();
        String code = FOOD_PREFIX + publicId;
        jdbc.update("""
                insert into foods
                    (public_id, code, display_name, nutrition_basis, source,
                     source_reference, revision, is_active, version)
                values (unhex(replace(?, '-', '')), ?, 'P10 mapped food',
                        'PER_100_G', 'CURATED', 'P10 pantry test', 1, true, 0)
                """, publicId.toString(), code);
        Long foodId = jdbc.queryForObject("""
                select id from foods
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, publicId.toString());
        jdbc.update("""
                insert into ingredient_foods
                    (ingredient_id, food_id, preparation_state, yield_factor,
                     is_primary)
                values (?, ?, 'RAW', 1.0000, false)
                """, ingredientId(fixture.ingredientPublicId()), foodId);
        return publicId;
    }

    private JsonNode getItem(UUID userPublicId, UUID pantryPublicId)
            throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/me/pantry/{publicId}", pantryPublicId)
                        .with(auth(userPublicId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Long ingredientId(UUID ingredientPublicId) {
        return jdbc.queryForObject("""
                select id from ingredients
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, ingredientPublicId.toString());
    }

    private Long userId(UUID userPublicId) {
        return jdbc.queryForObject("""
                select id from users
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, userPublicId.toString());
    }

    private Long lotId(UUID pantryPublicId) {
        return jdbc.queryForObject("""
                select id from pantry_items
                where public_id = unhex(replace(?, '-', ''))
                """, Long.class, pantryPublicId.toString());
    }

    private String unitCodeFor(UUID pantryPublicId) {
        return jdbc.queryForObject("""
                select unit.code
                from pantry_items item
                join measurement_units unit on unit.id = item.unit_id
                where item.public_id = unhex(replace(?, '-', ''))
                """, String.class, pantryPublicId.toString());
    }

    private UUID foodPublicIdFor(UUID pantryPublicId) {
        byte[] foodPublicId = jdbc.queryForObject("""
                select food.public_id
                from pantry_items item
                join foods food on food.id = item.food_id
                where item.public_id = unhex(replace(?, '-', ''))
                """, byte[].class, pantryPublicId.toString());
        return PantryIds.bytesToUuid(foodPublicId);
    }

    private int itemCountFor(UUID pantryPublicId) {
        return jdbc.queryForObject("""
                select count(*) from pantry_items
                where public_id = unhex(replace(?, '-', ''))
                """, Integer.class, pantryPublicId.toString());
    }

    private int eventCountFor(UUID pantryPublicId) {
        return jdbc.queryForObject("""
                select count(*) from pantry_item_events event
                join pantry_items item on item.id = event.pantry_item_id
                where item.public_id = unhex(replace(?, '-', ''))
                """, Integer.class, pantryPublicId.toString());
    }

    private BigDecimal quantityRemainingFor(UUID pantryPublicId) {
        return jdbc.queryForObject("""
                select quantity_remaining from pantry_items
                where public_id = unhex(replace(?, '-', ''))
                """, BigDecimal.class, pantryPublicId.toString());
    }

    private LocalDateTime closedAtFor(UUID pantryPublicId) {
        java.sql.Timestamp value = jdbc.queryForObject("""
                select closed_at from pantry_items
                where public_id = unhex(replace(?, '-', ''))
                """, java.sql.Timestamp.class, pantryPublicId.toString());
        return value.toLocalDateTime();
    }

    private LocalDateTime itemTimestamp(UUID pantryPublicId, String column) {
        String timestampColumn = switch (column) {
            case "created_at", "updated_at" -> column;
            default -> throw new IllegalArgumentException("Unsupported timestamp column");
        };
        java.sql.Timestamp value = jdbc.queryForObject(
                "select " + timestampColumn + " from pantry_items"
                        + " where public_id = unhex(replace(?, '-', ''))",
                java.sql.Timestamp.class,
                pantryPublicId.toString());
        return value.toLocalDateTime();
    }

    private List<PantryEventRow> eventsFor(UUID pantryPublicId) {
        return jdbc.query("""
                select event.event_type, event.quantity_delta, event.quantity_after,
                       event.occurred_at, item.user_id, event.pantry_item_id
                from pantry_item_events event
                join pantry_items item on item.id = event.pantry_item_id
                where item.public_id = unhex(replace(?, '-', ''))
                order by event.occurred_at asc, event.id asc
                """, (resultSet, rowNum) -> new PantryEventRow(
                        PantryItemEventType.valueOf(
                                resultSet.getString("event_type")),
                        resultSet.getBigDecimal("quantity_delta"),
                        resultSet.getBigDecimal("quantity_after"),
                        resultSet.getTimestamp("occurred_at").toLocalDateTime(),
                        resultSet.getLong("user_id"),
                        resultSet.getLong("pantry_item_id")), pantryPublicId.toString());
    }

    private static LocalDateTime parseTimestamp(JsonNode response, String field) {
        return LocalDateTime.parse(response.get(field).asText());
    }

    private static void assertMicrosecondPrecision(LocalDateTime value) {
        assertThat(value).isNotNull();
        assertThat(value.getNano() % 1_000).isEqualTo(0);
    }

    private static void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.isOpen() && entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }

    private record Fixture(UUID userPublicId, UUID ingredientPublicId) {
    }

    private record PantryEventRow(
            PantryItemEventType eventType,
            BigDecimal quantityDelta,
            BigDecimal quantityAfter,
            LocalDateTime occurredAt,
            Long ownerId,
            Long pantryItemId) {
    }
}
