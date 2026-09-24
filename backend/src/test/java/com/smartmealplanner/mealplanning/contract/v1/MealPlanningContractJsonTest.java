package com.smartmealplanner.mealplanning.contract.v1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.AllergenEvidenceStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealPlanningContractJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MealPlanningContractJson CONTRACT =
            MealPlanningContractJson.createDefault();

    @Test
    void readsTheSharedValidRequestWithoutAggregatingPantryLots() {
        MealPlanGenerationRequest request = CONTRACT.readRequest(fixture("valid_request.json"));

        assertThat(request.contractVersion()).isEqualTo("1");
        assertThat(request.pantryLots()).hasSize(2);
        assertThat(request.pantryLots())
                .extracting(MealPlanGenerationRequest.PantryLot::pantryItemPublicId)
                .doesNotHaveDuplicates();
        assertThatThrownBy(() -> request.pantryLots().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsCanonicalCatalogUnitsAndExactTwelvePlaceFactors() {
        MealPlanGenerationRequest request = CONTRACT.readRequest(
                fixture("valid_catalog_units_request.json"));
        assertThat(request.unitDefinitions())
                .extracting(MealPlanGenerationRequest.UnitDefinition::unitCode)
                .containsExactly("g", "kg", "mg", "mcg", "ml", "l", "tbsp", "floz",
                        "kcal", "kj", "piece");
        assertThat(request.unitDefinitions().get(7).toBaseFactor())
                .isEqualByComparingTo("29.573529562500");
        assertThat(request.unitDefinitions().get(9).toBaseFactor())
                .isEqualByComparingTo("0.239005736138");
        assertThat(request.nutritionTargets().get(0).unitCode()).isEqualTo("kcal");
        assertThat(request.pantryLots().get(0).unitCode()).isEqualTo("floz");
        assertThat(request.recipeCandidates().get(0).ingredients().get(0).unitCode())
                .isEqualTo("ml");
        assertThat(request.recipeCandidates().get(0).nutrition().values().get(0).unitCode())
                .isEqualTo("kj");
        assertThat(CONTRACT.readRequest(CONTRACT.writeRequest(request))).isEqualTo(request);
    }

    @Test
    void rejectsUppercaseUnitVariantsInEveryUnitField() throws Exception {
        for (String variant : new String[] {"G", "ML", "KCAL", " g "}) {
            ObjectNode request = catalogRequest();
            ((ObjectNode) ((ArrayNode) request.get("unitDefinitions")).get(0))
                    .put("unitCode", variant);
            assertInvalidRequest(request);
        }
        ObjectNode request = catalogRequest();
        ((ObjectNode) ((ArrayNode) request.get("nutritionTargets")).get(0))
                .put("unitCode", "KCAL");
        assertInvalidRequest(request);
        request = catalogRequest();
        ((ObjectNode) ((ArrayNode) request.get("unitDefinitions")).get(1))
                .put("baseUnitCode", "G");
        assertInvalidRequest(request);
        request = catalogRequest();
        ((ObjectNode) ((ArrayNode) request.get("pantryLots")).get(0))
                .put("unitCode", "ML");
        assertInvalidRequest(request);
        request = catalogRequest();
        ((ObjectNode) ((ArrayNode) ((ObjectNode) ((ArrayNode) request
                .get("recipeCandidates")).get(0)).get("ingredients")).get(0))
                .put("unitCode", "ML");
        assertInvalidRequest(request);
        request = catalogRequest();
        ((ObjectNode) ((ArrayNode) ((ObjectNode) ((ArrayNode) request
                .get("recipeCandidates")).get(0)).get("nutrition").get("values")).get(0))
                .put("unitCode", "KCAL");
        assertInvalidRequest(request);
    }

    @Test
    void rejectsInvalidFactorPrecisionSignAndUnsafeUnitGraphs() throws Exception {
        for (String factor : new String[] {"0", "-0.001", "0.1234567890123"}) {
            ObjectNode request = catalogRequest();
            ((ObjectNode) ((ArrayNode) request.get("unitDefinitions")).get(7))
                    .put("toBaseFactor", new BigDecimal(factor));
            assertInvalidRequest(request);
        }
        ObjectNode request = catalogRequest();
        ((ObjectNode) ((ArrayNode) request.get("unitDefinitions")).get(7))
                .put("baseUnitCode", "g");
        assertInvalidRequest(request);
        request = catalogRequest();
        ((ArrayNode) request.get("unitDefinitions")).remove(4);
        assertInvalidRequest(request);
    }

    @Test
    void semanticReferenceCodesStillRejectLowercase() throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(fixture("valid_request.json"));
        ((ObjectNode) ((ArrayNode) request.get("nutritionTargets")).get(0))
                .put("nutrientCode", "energy");
        assertInvalidRequest(request);
    }

    @Test
    void acceptsTheSharedBoundaryRequestAtLockedLimits() {
        MealPlanGenerationRequest request =
                CONTRACT.readRequest(fixture("valid_boundary_request.json"));

        assertThat(request.planning().days())
                .isEqualTo(MealPlanningContractLimits.MAX_PLAN_DAYS);
        assertThat(request.planning().requestedMealSlots())
                .hasSize(MealPlanningContractLimits.MAX_REQUESTED_MEAL_SLOTS);
        assertThat(MealPlanningContractLimits.MAX_EXPANDED_PLAN_SLOTS).isEqualTo(42);
    }

    @Test
    void generationDaysAcceptOneAndRejectZero() throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(fixture("valid_request.json"));
        ObjectNode planning = (ObjectNode) request.get("planning");
        planning.put("days", 1);
        assertThat(CONTRACT.readRequest(request.toString()).planning().days()).isEqualTo(1);

        planning.put("days", 0);
        assertThatThrownBy(() -> CONTRACT.readRequest(request.toString()))
                .isInstanceOf(MealPlanningContractValidationException.class);
    }

    @Test
    void acceptsOnlyCanonicalValidLocalDatesAcrossV1Transport() throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(fixture("valid_request.json"));
        ((ObjectNode) request.get("planning")).put("startDate", "2026-10-01");
        CONTRACT.readRequest(request.toString());

        ((ObjectNode) request.get("planning")).put("startDate", "2028-02-29");
        ((ObjectNode) ((ArrayNode) request.get("pantryLots")).get(0))
                .put("expiryDate", "2028-02-29");
        CONTRACT.readRequest(request.toString());

        ObjectNode succeeded = succeededResponse();
        ArrayNode entries = (ArrayNode) succeeded.get("entries");
        for (JsonNode entry : entries) { ((ObjectNode) entry).put("planDate", "2028-02-29"); }
        CONTRACT.readResponse(succeeded.toString());

        ObjectNode infeasible = (ObjectNode) JSON.readTree(
                fixture("valid_infeasible_response.json"));
        for (JsonNode slot : infeasible.get("unfilledSlots")) {
            ((ObjectNode) slot).put("planDate", "2028-02-29");
        }
        CONTRACT.readResponse(infeasible.toString());
    }

    @Test
    void rejectsNonCanonicalOrInvalidLocalDatesInEveryV1DateField() throws Exception {
        for (String invalid : new String[] {
                "2026-10-01T00:00:00", "2026-10-01Z", "2026-1-01",
                "2026/10/01", "2026-02-30", " 2026-10-01", "2026-10-01 "
        }) {
            ObjectNode requestDate = (ObjectNode) JSON.readTree(fixture("valid_request.json"));
            ((ObjectNode) requestDate.get("planning")).put("startDate", invalid);
            assertInvalidRequest(requestDate);

            ObjectNode expiryDate = (ObjectNode) JSON.readTree(fixture("valid_request.json"));
            ((ObjectNode) ((ArrayNode) expiryDate.get("pantryLots")).get(0))
                    .put("expiryDate", invalid);
            assertInvalidRequest(expiryDate);

            ObjectNode entryDate = succeededResponse();
            firstEntry(entryDate).put("planDate", invalid);
            assertInvalidResponse(entryDate);

            ObjectNode gapDate = (ObjectNode) JSON.readTree(
                    fixture("valid_infeasible_response.json"));
            ((ObjectNode) ((ArrayNode) gapDate.get("unfilledSlots")).get(0))
                    .put("planDate", invalid);
            assertInvalidResponse(gapDate);
        }
        ObjectNode numericDate = (ObjectNode) JSON.readTree(fixture("valid_request.json"));
        ((ObjectNode) numericDate.get("planning")).put("startDate", 20261001);
        assertInvalidRequest(numericDate);
    }

    @Test
    void sharedLimitsMatchEveryJavaContractConstant() throws Exception {
        JsonNode expected = JSON.readTree(fixture("contract_limits.json"));
        Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            int actual = MealPlanningContractLimits.class.getField(field.getKey())
                    .getInt(null);
            assertThat(actual).as(field.getKey()).isEqualTo(field.getValue().intValue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "invalid_contract_version_request.json",
        "invalid_decimal_string_request.json",
        "invalid_uuid_request.json",
        "invalid_unknown_field_request.json",
        "invalid_enum_code_request.json",
        "invalid_iso_date_request.json",
        "invalid_plan_days_request.json",
        "invalid_unit_definition_request.json",
        "oversized_allergens_request.json"
    })
    void rejectsSharedInvalidRequestFixtures(String fixtureName) {
        String json = fixture(fixtureName);

        assertThatThrownBy(() -> CONTRACT.readRequest(json))
                .isInstanceOf(MealPlanningContractValidationException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "valid_succeeded_response.json, SUCCEEDED",
        "valid_degraded_response.json, DEGRADED",
        "valid_infeasible_response.json, INFEASIBLE"
    })
    void readsSharedResponseFixtures(String fixtureName, GenerationStatus expectedStatus) {
        MealPlanGenerationResponse response = CONTRACT.readResponse(fixture(fixtureName));

        assertThat(response.status()).isEqualTo(expectedStatus);
        if (expectedStatus == GenerationStatus.INFEASIBLE) {
            assertThat(response.entries()).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, FREE_FROM",
        "3, CONTAINS",
        "4, MAY_CONTAIN",
        "5, UNKNOWN",
        "6, UNKNOWN",
        "7, UNKNOWN"
    })
    void declaredAllergenEvidenceFailsClosed(
            int ingredientSuffix, AllergenEvidenceStatus expected) {
        MealPlanGenerationRequest request = CONTRACT.readRequest(fixture("valid_request.json"));
        UUID ingredientId = UUID.fromString(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa" + ingredientSuffix);

        assertThat(request.hardConstraints().allergenCodes()).contains("PEANUT");
        AllergenEvidenceStatus evidence = request.allergenEvidenceFor(ingredientId, "PEANUT");
        assertThat(evidence).isEqualTo(expected);
        assertThat(evidence == AllergenEvidenceStatus.FREE_FROM)
                .isEqualTo(ingredientSuffix == 1);
    }

    @ParameterizedTest
    @CsvSource({
        "valid_succeeded_response.json, DEGRADED",
        "valid_degraded_response.json, INFEASIBLE",
        "valid_infeasible_response.json, SUCCEEDED"
    })
    void rejectsStatusesWithInvalidShapes(String fixtureName, GenerationStatus status)
            throws Exception {
        ObjectNode response = (ObjectNode) JSON.readTree(fixture(fixtureName));
        response.put("status", status.name());

        assertInvalidResponse(response);
    }

    @Test
    void rejectsDuplicateMissingAndUnknownScoreComponents() throws Exception {
        ObjectNode duplicate = succeededResponse();
        ((ObjectNode) components(duplicate).get(1)).put("componentCode", "PANTRY_COVERAGE");
        assertInvalidResponse(duplicate);

        ObjectNode missing = succeededResponse();
        components(missing).remove(6);
        assertInvalidResponse(missing);

        ObjectNode unknown = succeededResponse();
        ((ObjectNode) components(unknown).get(0))
                .put("componentCode", "UNRECOGNIZED_COMPONENT");
        assertInvalidResponse(unknown);
    }

    @Test
    void rejectsInvalidScoreValuesAndWeights() throws Exception {
        for (double value : new double[] {-0.01, 1.01}) {
            ObjectNode response = succeededResponse();
            ((ObjectNode) components(response).get(0)).put("value", value);
            assertInvalidResponse(response);
        }
        for (double weight : new double[] {-0.20, 0.10}) {
            ObjectNode response = succeededResponse();
            ((ObjectNode) components(response).get(6)).put("weight", weight);
            assertInvalidResponse(response);
        }
        ObjectNode wrongCoverageWeight = succeededResponse();
        ((ObjectNode) components(wrongCoverageWeight).get(0)).put("weight", 0.30);
        assertInvalidResponse(wrongCoverageWeight);
        for (double total : new double[] {-0.21, 1.01}) {
            ObjectNode response = succeededResponse();
            firstEntry(response).put("totalScore", total);
            assertInvalidResponse(response);
        }
    }

    @Test
    void infeasibleCannotCarryAFabricatedRecommendationResult() throws Exception {
        ObjectNode response = (ObjectNode) JSON.readTree(
                fixture("valid_infeasible_response.json"));
        response.putArray("recommendationResults")
                .addObject().put("recipePublicId", "fake");

        assertInvalidResponse(response);
    }

    @Test
    void rejectsCombinedSlotCountAboveTheGenerationLimit() throws Exception {
        ObjectNode response = succeededResponse();
        response.put("status", "DEGRADED");
        ArrayNode unfilled = response.putArray("unfilledSlots");
        for (int day = 0; day < MealPlanningContractLimits.MAX_PLAN_DAYS
                && unfilled.size() < MealPlanningContractLimits.MAX_EXPANDED_PLAN_SLOTS - 1;
                day++) {
            for (MealSlotCode slot : MealSlotCode.values()) {
                if (unfilled.size() == MealPlanningContractLimits.MAX_EXPANDED_PLAN_SLOTS - 1) {
                    break;
                }
                ObjectNode item = unfilled.addObject();
                item.put("planDate", java.time.LocalDate.of(2026, 10, 2)
                        .plusDays(day).toString());
                item.put("mealSlotCode", slot.name());
                item.put("reasonCode", "NO_ELIGIBLE_RECIPE");
                item.putNull("explanation");
            }
        }
        assertThat(response.get("entries").size() + unfilled.size()).isEqualTo(43);
        assertInvalidResponse(response);
    }

    @Test
    void rejectsOutOfRangeTotalScore() {
        assertThatThrownBy(() -> CONTRACT.readResponse(
                fixture("invalid_score_range_response.json")))
                .isInstanceOf(MealPlanningContractValidationException.class);
    }

    @Test
    void serializesAndDeserializesTheSameContractShape() {
        MealPlanGenerationRequest request = CONTRACT.readRequest(fixture("valid_request.json"));
        MealPlanGenerationResponse response = CONTRACT.readResponse(
                fixture("valid_degraded_response.json"));

        assertThat(CONTRACT.readRequest(CONTRACT.writeRequest(request))).isEqualTo(request);
        assertThat(CONTRACT.readResponse(CONTRACT.writeResponse(response))).isEqualTo(response);
    }

    private static ObjectNode succeededResponse() throws IOException {
        return (ObjectNode) JSON.readTree(fixture("valid_succeeded_response.json"));
    }

    private static ObjectNode catalogRequest() throws IOException {
        return (ObjectNode) JSON.readTree(fixture("valid_catalog_units_request.json"));
    }

    private static void assertInvalidRequest(ObjectNode request) {
        assertThatThrownBy(() -> CONTRACT.readRequest(request.toString()))
                .isInstanceOf(MealPlanningContractValidationException.class);
    }

    private static ObjectNode firstEntry(ObjectNode response) {
        return (ObjectNode) ((ArrayNode) response.get("entries")).get(0);
    }

    private static ArrayNode components(ObjectNode response) {
        return (ArrayNode) firstEntry(response).get("scoreComponents");
    }

    private static void assertInvalidResponse(ObjectNode response) {
        assertThatThrownBy(() -> CONTRACT.readResponse(response.toString()))
                .isInstanceOf(MealPlanningContractValidationException.class);
    }

    private static String fixture(String name) {
        String path = "/contract-fixtures/meal_planning/v1/" + name;
        try (InputStream input = MealPlanningContractJsonTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing contract fixture " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read contract fixture " + path, exception);
        }
    }
}
