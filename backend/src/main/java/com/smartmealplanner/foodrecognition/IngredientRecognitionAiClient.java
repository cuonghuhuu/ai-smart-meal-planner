package com.smartmealplanner.foodrecognition;

public interface IngredientRecognitionAiClient {
    IngredientRecognitionResult detect(byte[] imageBytes, String contentType);
}
