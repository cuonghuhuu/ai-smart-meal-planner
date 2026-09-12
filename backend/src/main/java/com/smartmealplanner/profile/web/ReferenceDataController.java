package com.smartmealplanner.profile.web;

import java.util.List;

import com.smartmealplanner.profile.application.ReferenceDataService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    public ReferenceDataController(
            ReferenceDataService referenceDataService) {

        this.referenceDataService =
                referenceDataService;
    }

    @GetMapping({
            "/reference/activity-levels",
            "/activity-levels"
    })
    public List<ActivityLevelItemResponse> getActivityLevels() {

        return referenceDataService.getActivityLevels();
    }

    @GetMapping({
            "/reference/nutrition-goals",
            "/nutrition-goals"
    })
    public List<NutritionGoalItemResponse> getNutritionGoals() {

        return referenceDataService.getNutritionGoals();
    }

    @GetMapping({
            "/reference/dietary-preferences",
            "/dietary-preferences"
    })
    public List<DietaryPreferenceItemResponse> getDietaryPreferences() {

        return referenceDataService.getDietaryPreferences();
    }

    @GetMapping({
            "/reference/allergens",
            "/allergens"
    })
    public List<AllergenItemResponse> getAllergens() {

        return referenceDataService.getAllergens();
    }
}
