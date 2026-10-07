package com.smartmealplanner.recommendation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmealplanner.recommendation.contract.v1.RecipeRankingContractJson;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RecipeRecommendationConfiguration {
    @Bean
    RecipeRankingContractJson recipeRankingContractJson(
            ObjectMapper mapper, Validator validator) {
        return new RecipeRankingContractJson(mapper, validator);
    }
}
