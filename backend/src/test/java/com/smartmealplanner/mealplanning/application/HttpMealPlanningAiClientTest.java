package com.smartmealplanner.mealplanning.application;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpMealPlanningAiClientTest {
    private final MealPlanningContractJson contract = MealPlanningContractJson.createDefault();
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void close() {
        if (server != null) { server.stop(0); }
    }

    @Test
    void sendsTypedJsonInternalTokenAndCorrelationWithoutEndUserJwt() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        String responseBody = fixture("valid_degraded_response.json");
        server = start(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestURI().getPath())
                    .isEqualTo("/internal/v1/meal-plans/generate");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type"))
                    .isEqualTo("application/json");
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"))
                    .isEqualTo("test-internal-token");
            assertThat(exchange.getRequestHeaders().getFirst("X-Request-Id"))
                    .isEqualTo("11111111-1111-4111-8111-111111111111");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, responseBody);
        });
        MealPlanGenerationRequest request = request();
        assertThat(client(Duration.ofSeconds(3), 1024 * 1024).generate(request).status().name())
                .isEqualTo("DEGRADED");
        assertThat(contract.readRequest(received.get())).isEqualTo(request);
    }

    @Test
    void mapsTimeoutUnavailableServerFailureMalformedAndOversizedResponse() throws Exception {
        String responseBody = fixture("valid_degraded_response.json");
        server = start(exchange -> {
            try { Thread.sleep(500); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            reply(exchange, 200, responseBody);
        });
        rejects(client(Duration.ofMillis(50), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_SERVICE_TIMEOUT);
        server.stop(0);
        server = null;
        // The bound ephemeral port is now closed: connection refusal is transport unavailability.
        HttpMealPlanningAiClient unavailable = new HttpMealPlanningAiClient(
                properties(URI.create("http://127.0.0.1:1"), Duration.ofMillis(300), 1024 * 1024),
                contract, json);
        rejects(unavailable, MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE);
        server = start(exchange -> reply(exchange, 503, "{\"code\":\"AI_SERVICE_UNAVAILABLE\"}"));
        rejects(client(Duration.ofSeconds(3), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE);
        server.stop(0);
        server = start(exchange -> reply(exchange, 200, "{broken"));
        rejects(client(Duration.ofSeconds(3), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
        server.stop(0);
        server = start(exchange -> reply(exchange, 503,
                "{\"code\":\"AI_SEARCH_BUDGET_EXHAUSTED\"}"));
        rejects(client(Duration.ofSeconds(3), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
        server.stop(0);
        server = start(exchange -> reply(exchange, 503, "{broken"));
        rejects(client(Duration.ofSeconds(3), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE);
        server.stop(0);
        server = start(exchange -> reply(exchange, 200, responseBody));
        rejects(client(Duration.ofSeconds(3), 1024),
                MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
    }

    @Test
    void mapsTechnicalSearchBudgetCodeNotHumanMessage() throws Exception {
        server = start(exchange -> reply(exchange, 503,
                "{\"code\":\"AI_SEARCH_BUDGET_EXHAUSTED\","
                + "\"requestId\":\"11111111-1111-4111-8111-111111111111\","
                + "\"algorithmVersion\":\"HEURISTIC_MEAL_PLAN_V1\","
                + "\"message\":\"arbitrary human text\"}"));
        rejects(client(Duration.ofSeconds(3), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_SEARCH_BUDGET_EXHAUSTED);
        server.stop(0);
        server = start(exchange -> reply(exchange, 503,
                "{\"code\":\"AI_SEARCH_BUDGET_EXHAUSTED\","
                + "\"requestId\":\"22222222-2222-4222-8222-222222222222\","
                + "\"algorithmVersion\":\"HEURISTIC_MEAL_PLAN_V1\"}"));
        rejects(client(Duration.ofSeconds(3), 1024 * 1024),
                MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
    }

    @Test
    void rejectsOversizedChunkedBodyWithoutContentLength() throws Exception {
        server = start(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0); // HTTP chunked; no Content-Length.
            try (var output = exchange.getResponseBody()) {
                byte[] chunk = "x".repeat(700).getBytes(StandardCharsets.UTF_8);
                try {
                    output.write(chunk);
                    output.write(chunk);
                } catch (java.io.IOException ignored) {
                    // The bounded Java subscriber is allowed to cancel mid-stream.
                }
            }
        });
        rejects(client(Duration.ofSeconds(3), 1024),
                MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
    }

    private void rejects(HttpMealPlanningAiClient client, MealPlanningIntegrationFailure expected)
            throws Exception {
        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(MealPlanningIntegrationException.class,
                        error -> assertThat(error.failure()).isEqualTo(expected));
    }

    private HttpMealPlanningAiClient client(Duration timeout, int maxBytes) {
        return new HttpMealPlanningAiClient(properties(URI.create("http://127.0.0.1:"
                + server.getAddress().getPort()), timeout, maxBytes), contract, json);
    }

    private static AiServiceProperties properties(URI uri, Duration timeout, int maxBytes) {
        return new AiServiceProperties(uri, "test-internal-token", Duration.ofMillis(300),
                timeout, maxBytes);
    }

    private MealPlanGenerationRequest request() throws Exception {
        return contract.readRequest(fixture("valid_request.json"));
    }

    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("..", "contract_fixtures", "meal_planning", "v1", name));
    }

    private static HttpServer start(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meal-plans/generate", handler);
        server.start();
        return server;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange,
            int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
