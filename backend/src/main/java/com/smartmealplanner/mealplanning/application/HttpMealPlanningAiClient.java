package com.smartmealplanner.mealplanning.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationRequest;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanGenerationResponse;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractLimits;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractValidationException;

import org.springframework.stereotype.Service;

/** One bounded HTTP call, no retry, no forwarded user credential. */
@Service
public class HttpMealPlanningAiClient implements MealPlanningAiClient {
    private static final String PATH = "/internal/v1/meal-plans/generate";
    private static final String BUDGET_ERROR = "AI_SEARCH_BUDGET_EXHAUSTED";
    private final AiServiceProperties properties;
    private final MealPlanningContractJson contract;
    private final ObjectMapper json;
    private final HttpClient http;

    public HttpMealPlanningAiClient(AiServiceProperties properties,
            MealPlanningContractJson contract, ObjectMapper json) {
        this(properties, contract, json, HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    HttpMealPlanningAiClient(AiServiceProperties properties,
            MealPlanningContractJson contract, ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.contract = contract;
        this.json = json;
        this.http = http;
    }

    @Override
    public MealPlanGenerationResponse generate(MealPlanGenerationRequest request) {
        String token = properties.internalServiceToken();
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n")) {
            throw failure(MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE);
        }
        byte[] body = contract.writeRequest(request).getBytes(StandardCharsets.UTF_8);
        if (body.length > MealPlanningContractLimits.MAX_REQUEST_BYTES) {
            throw failure(MealPlanningIntegrationFailure.SNAPSHOT_LIMIT_EXCEEDED);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(properties.baseUrl().resolve(PATH))
                .header("Content-Type", "application/json")
                .header("X-Internal-Service-Token", token)
                .header("X-Request-Id", request.requestId().toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<byte[]> response = exchange(httpRequest, properties.responseTimeout());
        if (response.statusCode() == 200) {
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("application/json")) {
                throw failure(MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
            }
            try {
                return contract.readResponse(new String(response.body(), StandardCharsets.UTF_8));
            } catch (MealPlanningContractValidationException exception) {
                throw new MealPlanningIntegrationException(
                        MealPlanningIntegrationFailure.AI_BAD_RESPONSE, exception);
            }
        }
        if (response.statusCode() == 503 && isSearchBudgetError(response.body(), request)) {
            throw failure(MealPlanningIntegrationFailure.AI_SEARCH_BUDGET_EXHAUSTED);
        }
        if (response.statusCode() == 504) {
            throw failure(MealPlanningIntegrationFailure.AI_SERVICE_TIMEOUT);
        }
        if (response.statusCode() >= 500 || response.statusCode() == 401
                || response.statusCode() == 403) {
            throw failure(MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE);
        }
        throw failure(MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
    }

    private HttpResponse<byte[]> exchange(HttpRequest request, Duration timeout) {
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request,
                ignored -> new LimitedBodySubscriber(properties.maxResponseBytes()));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.AI_SERVICE_TIMEOUT, exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (hasCause(cause, ResponseTooLargeException.class)) {
                throw new MealPlanningIntegrationException(
                        MealPlanningIntegrationFailure.AI_BAD_RESPONSE, cause);
            }
            if (hasCause(cause, HttpTimeoutException.class)) {
                throw new MealPlanningIntegrationException(
                        MealPlanningIntegrationFailure.AI_SERVICE_TIMEOUT, cause);
            }
            if (cause instanceof ConnectException || cause instanceof IOException) {
                throw new MealPlanningIntegrationException(
                        MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE, cause);
            }
            throw new MealPlanningIntegrationException(
                    MealPlanningIntegrationFailure.AI_SERVICE_UNAVAILABLE, cause);
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) { return true; }
        }
        return false;
    }

    private boolean isSearchBudgetError(byte[] body, MealPlanGenerationRequest request) {
        try {
            JsonNode error = json.readTree(body);
            if (error == null || !BUDGET_ERROR.equals(error.path("code").asText())) {
                return false;
            }
            if (!request.requestId().toString().equals(error.path("requestId").asText())
                    || !request.algorithmVersion().name().equals(
                            error.path("algorithmVersion").asText())) {
                throw failure(MealPlanningIntegrationFailure.AI_BAD_RESPONSE);
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static MealPlanningIntegrationException failure(MealPlanningIntegrationFailure kind) {
        return new MealPlanningIntegrationException(kind);
    }

    private static final class ResponseTooLargeException extends IOException {
        ResponseTooLargeException() { super("AI response exceeded configured bound"); }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        LimitedBodySubscriber(int maximum) { this.maximum = maximum; }

        @Override
        public CompletionStage<byte[]> getBody() { return body; }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                int count = chunk.remaining();
                if (count > maximum - output.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[count];
                chunk.get(bytes);
                output.writeBytes(bytes);
            }
        }

        @Override
        public void onError(Throwable error) { body.completeExceptionally(error); }

        @Override
        public void onComplete() { body.complete(output.toByteArray()); }
    }
}
