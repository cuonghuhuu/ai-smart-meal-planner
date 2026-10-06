package com.smartmealplanner.foodrecognition.web;

import com.smartmealplanner.foodrecognition.IngredientRecognitionAiClient;
import com.smartmealplanner.foodrecognition.IngredientRecognitionResult;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authenticated gateway; clients never call FastAPI directly. */
@RestController
@RequestMapping("/api/v1/food-recognition")
public class IngredientRecognitionController {
    private final IngredientRecognitionAiClient recognition;

    public IngredientRecognitionController(IngredientRecognitionAiClient recognition) {
        this.recognition = recognition;
    }

    @PostMapping(
            path = "/ingredients:detect",
            consumes = {
                    MediaType.IMAGE_JPEG_VALUE,
                    MediaType.IMAGE_PNG_VALUE,
                    "image/webp",
                    MediaType.APPLICATION_OCTET_STREAM_VALUE
            },
            produces = MediaType.APPLICATION_JSON_VALUE)
    public IngredientRecognitionResult detect(
            @RequestHeader(
                    name = HttpHeaders.CONTENT_TYPE,
                    defaultValue = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            String contentType,
            @RequestBody byte[] imageBytes) {
        return recognition.detect(imageBytes,
                contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT));
    }
}
