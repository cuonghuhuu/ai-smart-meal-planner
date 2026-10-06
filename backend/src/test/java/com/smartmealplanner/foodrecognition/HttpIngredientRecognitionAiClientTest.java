package com.smartmealplanner.foodrecognition;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.shared.application.AiServiceProperties;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpIngredientRecognitionAiClientTest {
    private HttpServer server;

    @AfterEach
    void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsRawImageAndInternalCredentialAndDecodesVietnameseUtf8() throws Exception {
        AtomicReference<byte[]> received = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/food-recognition/ingredients:detect", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type"))
                    .isEqualTo("image/jpeg");
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Service-Token"))
                    .isEqualTo("test-internal-token");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            received.set(exchange.getRequestBody().readAllBytes());

            byte[] body = """
                    {"algorithmVersion":"YOLO11N_INGREDIENT_V1",
                     "imageWidth":595,"imageHeight":336,
                     "detections":[{"classId":8,"code":"THIT_LON",
                     "nameVi":"Thịt lợn","confidence":0.82,
                     "box":{"x1":1.0,"y1":2.0,"x2":30.0,"y2":40.0}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        byte[] image = new byte[]{1, 2, 3, 4};
        IngredientRecognitionResult result = client(Duration.ofSeconds(2))
                .detect(image, "image/jpeg");

        assertThat(received.get()).containsExactly(image);
        assertThat(result.algorithmVersion()).isEqualTo("YOLO11N_INGREDIENT_V1");
        assertThat(result.detections()).hasSize(1);
        assertThat(result.detections().getFirst().nameVi()).isEqualTo("Thịt lợn");
        assertThat(result.detections().getFirst().code()).isEqualTo("THIT_LON");
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedImagesBeforeNetworkCall() {
        HttpIngredientRecognitionAiClient client = new HttpIngredientRecognitionAiClient(
                properties(URI.create("http://127.0.0.1:1"), Duration.ofSeconds(1)),
                new ObjectMapper());

        assertFailure(() -> client.detect(new byte[0], "image/jpeg"),
                IngredientRecognitionFailure.INVALID_IMAGE);
        assertFailure(() -> client.detect(
                        new byte[HttpIngredientRecognitionAiClient.MAX_IMAGE_BYTES + 1],
                        "image/jpeg"),
                IngredientRecognitionFailure.IMAGE_TOO_LARGE);
        assertFailure(() -> client.detect(new byte[]{1}, "text/plain"),
                IngredientRecognitionFailure.INVALID_IMAGE);
    }

    private HttpIngredientRecognitionAiClient client(Duration timeout) {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new HttpIngredientRecognitionAiClient(
                properties(uri, timeout),
                new ObjectMapper());
    }

    private static AiServiceProperties properties(URI uri, Duration timeout) {
        return new AiServiceProperties(
                uri,
                "test-internal-token",
                Duration.ofMillis(300),
                timeout,
                1024 * 1024);
    }

    private static void assertFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            IngredientRecognitionFailure expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        IngredientRecognitionException.class,
                        exception -> assertThat(exception.failure()).isEqualTo(expected));
    }
}
