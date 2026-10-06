package com.smartmealplanner.mealplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractJson;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MealPlanningAiConfiguration {
    @Bean
    MealPlanningContractJson mealPlanningContractJson(ObjectMapper mapper, Validator validator) {
        return new MealPlanningContractJson(mapper, validator);
    }
}
