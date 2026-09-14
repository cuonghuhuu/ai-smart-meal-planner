package com.smartmealplanner.nutrition.web;

import java.util.List;

import com.smartmealplanner.nutrition.application.NutritionReferenceService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for the stable Nutrition nutrient reference vocabulary. */
@RestController
@RequestMapping("/api/v1/reference")
public class NutritionReferenceController {

    private final NutritionReferenceService nutritionReferenceService;

    public NutritionReferenceController(
            NutritionReferenceService nutritionReferenceService) {

        this.nutritionReferenceService = nutritionReferenceService;
    }

    @GetMapping("/nutrients")
    public List<NutritionReferenceNutrientResponse> getNutrients() {

        return nutritionReferenceService.getNutrients()
                .stream()
                .map(NutritionWebMapper::toResponse)
                .toList();
    }
}
