package com.smartmealplanner.recommendation.contract.v1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeRankingContractJsonTest {
    private final RecipeRankingContractJson json = RecipeRankingContractJson.createDefault();

    @Test
    void sharedValidFixturesRoundTrip() {
        RecipeRankingRequest request = json.readRequest(fixture("valid_request.json"));
        assertThat(request.algorithmVersion())
                .isEqualTo(RecipeRankingContractCodes.AlgorithmVersion.HEURISTIC_RECIPE_RANK_V1);
        assertThat(json.readResponse(fixture("valid_succeeded_response.json")).rankedRecipes())
                .hasSize(2);
        assertThat(json.readResponse(fixture("valid_infeasible_response.json")).rankedRecipes())
                .isEmpty();
        assertThat(json.writeRequest(request)).contains("contractVersion");
    }

    @Test
    void sharedInvalidFixturesAreRejected() {
        assertThatThrownBy(() -> json.readRequest(fixture("invalid_algorithm_version_request.json")))
                .isInstanceOf(RecipeRankingContractValidationException.class);
        assertThatThrownBy(() -> json.readRequest(fixture("invalid_unknown_field_request.json")))
                .isInstanceOf(RecipeRankingContractValidationException.class);
        assertThatThrownBy(() -> json.readResponse(
                fixture("invalid_noncontiguous_rank_response.json")))
                .isInstanceOf(RecipeRankingContractValidationException.class);
        assertThatThrownBy(() -> json.readResponse(
                fixture("invalid_score_components_response.json")))
                .isInstanceOf(RecipeRankingContractValidationException.class);
    }

    private static String fixture(String name) {
        String path = "/contract-fixtures/recipe_recommendation/v1/" + name;
        try (InputStream input = RecipeRankingContractJsonTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing shared fixture " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read shared fixture " + path, exception);
        }
    }
}
