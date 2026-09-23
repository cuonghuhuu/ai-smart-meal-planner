package com.smartmealplanner.mealplanning.contract.v1;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Strict JSON codec and Jakarta validator for the internal version-1 contract. */
public final class MealPlanningContractJson {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MealPlanningContractJson(ObjectMapper objectMapper, Validator validator) {
        ObjectMapper strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        strictMapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        this.objectMapper = strictMapper;
        this.validator = validator;
    }

    public static MealPlanningContractJson createDefault() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return new MealPlanningContractJson(mapper, validator);
    }

    public MealPlanGenerationRequest readRequest(String json) {
        return read(json, MealPlanGenerationRequest.class);
    }

    public MealPlanGenerationResponse readResponse(String json) {
        return read(json, MealPlanGenerationResponse.class);
    }

    public String writeRequest(MealPlanGenerationRequest request) {
        return writeValidated(request);
    }

    public String writeResponse(MealPlanGenerationResponse response) {
        return writeValidated(response);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            T value = objectMapper.readValue(json, type);
            validate(value);
            return value;
        } catch (MealPlanningContractValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MealPlanningContractValidationException(
                    List.of("invalid JSON: " + safeMessage(exception)));
        }
    }

    private String writeValidated(Object value) {
        validate(value);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new MealPlanningContractValidationException(
                    List.of("cannot serialize contract: " + safeMessage(exception)));
        }
    }

    private <T> void validate(T value) {
        List<String> violations = validator.validate(value).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(MealPlanningContractJson::message)
                .toList();
        if (!violations.isEmpty()) {
            throw new MealPlanningContractValidationException(violations);
        }
    }

    private static String message(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
