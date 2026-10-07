package com.smartmealplanner.foodrecognition;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.shared.application.AiServiceProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Authenticated Java-to-FastAPI adapter for local YOLO inference. */
@Service
public class HttpIngredientRecognitionAiClient implements IngredientRecognitionAiClient {
    static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final String PATH = "/internal/v1/food-recognition/ingredients:detect";
    private static final Set<String> CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/octet-stream");

    private final AiServiceProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public HttpIngredientRecognitionAiClient(
            AiServiceProperties properties,
            ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    HttpIngredientRecognitionAiClient(
            AiServiceProperties properties,
            ObjectMapper json,
            HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.http = http;
    }

    @Override
    public IngredientRecognitionResult detect(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw failure(IngredientRecognitionFailure.INVALID_IMAGE);
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            throw failure(IngredientRecognitionFailure.IMAGE_TOO_LARGE);
        }

        String normalizedContentType = normalizeContentType(contentType);
        String token = properties.internalServiceToken();
        if (token == null || token.isBlank()
                || token.contains("\r") || token.contains("\n")) {
            throw failure(IngredientRecognitionFailure.AI_SERVICE_UNAVAILABLE);
        }

        HttpRequest request = HttpRequest.newBuilder(properties.baseUrl().resolve(PATH))
                .header("Content-Type", normalizedContentType)
                .header("X-Internal-Service-Token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                .build();

        HttpResponse<byte[]> response = exchange(request, properties.responseTimeout());
        if (response.body().length > properties.maxResponseBytes()) {
            throw failure(IngredientRecognitionFailure.AI_BAD_RESPONSE);
        }

        if (response.statusCode() == 200) {
            String responseType = response.headers()
                    .firstValue("Content-Type").orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!responseType.startsWith("application/json")) {
                throw failure(IngredientRecognitionFailure.AI_BAD_RESPONSE);
            }
            try {
                return json.readValue(response.body(), IngredientRecognitionResult.class);
            } catch (IOException | IllegalArgumentException exception) {
                throw new IngredientRecognitionException(
                        IngredientRecognitionFailure.AI_BAD_RESPONSE, exception);
            }
        }

        if (response.statusCode() == 413) {
            throw failure(IngredientRecognitionFailure.IMAGE_TOO_LARGE);
        }
        if (response.statusCode() == 422) {
            throw failure(IngredientRecognitionFailure.INVALID_IMAGE);
        }
        if (response.statusCode() == 504) {
            throw failure(IngredientRecognitionFailure.AI_SERVICE_TIMEOUT);
        }
        if (response.statusCode() >= 500
                || response.statusCode() == 401
                || response.statusCode() == 403) {
            throw failure(IngredientRecognitionFailure.AI_SERVICE_UNAVAILABLE);
        }
        throw failure(IngredientRecognitionFailure.AI_BAD_RESPONSE);
    }

    private HttpResponse<byte[]> exchange(HttpRequest request, Duration timeout) {
        var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IngredientRecognitionException(
                    IngredientRecognitionFailure.AI_SERVICE_TIMEOUT, exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IngredientRecognitionException(
                    IngredientRecognitionFailure.AI_SERVICE_UNAVAILABLE, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (hasCause(cause, HttpTimeoutException.class)) {
                throw new IngredientRecognitionException(
                        IngredientRecognitionFailure.AI_SERVICE_TIMEOUT, cause);
            }
            if (cause instanceof ConnectException || cause instanceof IOException) {
                throw new IngredientRecognitionException(
                        IngredientRecognitionFailure.AI_SERVICE_UNAVAILABLE, cause);
            }
            throw new IngredientRecognitionException(
                    IngredientRecognitionFailure.AI_SERVICE_UNAVAILABLE, cause);
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPES.contains(normalized)) {
            throw failure(IngredientRecognitionFailure.INVALID_IMAGE);
        }
        return normalized;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static IngredientRecognitionException failure(
            IngredientRecognitionFailure failure) {
        return new IngredientRecognitionException(failure);
    }
}
