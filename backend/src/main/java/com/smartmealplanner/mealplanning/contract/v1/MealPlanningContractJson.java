package com.smartmealplanner.mealplanning.contract.v1;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Strict JSON codec and Jakarta validator for the internal version-1 contract. */
public final class MealPlanningContractJson {
    private static final Pattern STRICT_DATE_PATTERN = Pattern.compile(
            "\\A[0-9]{4}-[0-9]{2}-[0-9]{2}\\z");
    private static final DateTimeFormatter STRICT_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd")
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

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
        SimpleModule contractDates = new SimpleModule("meal-planning-contract-v1-dates");
        contractDates.addDeserializer(LocalDate.class, new StrictLocalDateDeserializer());
        strictMapper.registerModule(contractDates);
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

    private static final class StrictLocalDateDeserializer extends JsonDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
            }
            String value = parser.getText();
            if (!STRICT_DATE_PATTERN.matcher(value).matches()) {
                throw context.weirdStringException(value, LocalDate.class,
                        "must use the exact YYYY-MM-DD format");
            }
            try {
                return LocalDate.parse(value, STRICT_DATE_FORMATTER);
            } catch (DateTimeParseException exception) {
                throw context.weirdStringException(value, LocalDate.class,
                        "must be a valid calendar date in YYYY-MM-DD format");
            }
        }
    }
}
