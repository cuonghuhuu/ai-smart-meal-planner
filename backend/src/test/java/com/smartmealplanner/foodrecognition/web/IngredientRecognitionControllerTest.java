package com.smartmealplanner.foodrecognition.web;

import java.util.List;

import com.smartmealplanner.auth.SecurityConfiguration;
import com.smartmealplanner.foodrecognition.IngredientRecognitionAiClient;
import com.smartmealplanner.foodrecognition.IngredientRecognitionException;
import com.smartmealplanner.foodrecognition.IngredientRecognitionFailure;
import com.smartmealplanner.foodrecognition.IngredientRecognitionResult;
import com.smartmealplanner.shared.web.ApiExceptionHandler;
import com.smartmealplanner.shared.web.ApiProblems;
import com.smartmealplanner.shared.web.RequestIdFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IngredientRecognitionController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfiguration.class,
        ApiProblems.class,
        ApiExceptionHandler.class,
        IngredientRecognitionExceptionHandler.class,
        RequestIdFilter.class
})
class IngredientRecognitionControllerTest {
    private static final String PATH =
            "/api/v1/food-recognition/ingredients:detect";

    @Autowired MockMvc mvc;
    @MockitoBean IngredientRecognitionAiClient recognition;

    @Test
    void unauthenticatedRecognitionIsRejected() throws Exception {
        mvc.perform(post(PATH)
                        .with(csrf())
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recognition);
    }

    @Test
    void bearerRecognitionDoesNotRequireCsrfEvenWhenRefreshCookieExists() throws Exception {
        var result = new IngredientRecognitionResult(
                "YOLO11N_INGREDIENT_V1",
                595,
                336,
                List.of());
        when(recognition.detect(any(byte[].class), eq("image/jpeg")))
                .thenReturn(result);

        mvc.perform(post(PATH)
                        .with(jwt())
                        .header("Authorization", "Bearer test-access-token")
                        .cookie(new jakarta.servlet.http.Cookie(
                                "__Host-smartmeal_refresh",
                                "opaque-refresh"))
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithmVersion")
                        .value("YOLO11N_INGREDIENT_V1"));
    }

    @Test
    void authenticatedRequestReturnsVietnameseRecognitionContract() throws Exception {
        var result = new IngredientRecognitionResult(
                "YOLO11N_INGREDIENT_V1",
                595,
                336,
                List.of(new IngredientRecognitionResult.Detection(
                        8,
                        "THIT_LON",
                        "Thịt lợn",
                        0.82,
                        new IngredientRecognitionResult.BoundingBox(
                                1.0, 2.0, 30.0, 40.0))));
        when(recognition.detect(any(byte[].class), eq("image/jpeg")))
                .thenReturn(result);

        mvc.perform(post(PATH)
                        .with(jwt())
                        .with(csrf())
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithmVersion")
                        .value("YOLO11N_INGREDIENT_V1"))
                .andExpect(jsonPath("$.detections[0].code").value("THIT_LON"))
                .andExpect(jsonPath("$.detections[0].nameVi").value("Thịt lợn"))
                .andExpect(jsonPath("$.detections[0].confidence").value(0.82));

        verify(recognition).detect(any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void unavailableAiUsesSafeServiceUnavailableProblem() throws Exception {
        when(recognition.detect(any(byte[].class), eq("image/jpeg")))
                .thenThrow(new IngredientRecognitionException(
                        IngredientRecognitionFailure.AI_SERVICE_UNAVAILABLE,
                        new IllegalStateException("private FastAPI detail")));

        mvc.perform(post(PATH)
                        .with(jwt())
                        .with(csrf())
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("FOOD_RECOGNITION_AI_SERVICE_UNAVAILABLE"));
    }
}
