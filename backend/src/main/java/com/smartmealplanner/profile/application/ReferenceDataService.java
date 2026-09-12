package com.smartmealplanner.profile.application;

import java.util.List;

import com.smartmealplanner.profile.persistence.ActivityLevel;
import com.smartmealplanner.profile.persistence.ActivityLevelRepository;
import com.smartmealplanner.profile.persistence.Allergen;
import com.smartmealplanner.profile.persistence.AllergenRepository;
import com.smartmealplanner.profile.persistence.DietaryPreference;
import com.smartmealplanner.profile.persistence.DietaryPreferenceRepository;
import com.smartmealplanner.profile.persistence.NutritionGoal;
import com.smartmealplanner.profile.persistence.NutritionGoalRepository;
import com.smartmealplanner.profile.web.ActivityLevelItemResponse;
import com.smartmealplanner.profile.web.AllergenItemResponse;
import com.smartmealplanner.profile.web.DietaryPreferenceItemResponse;
import com.smartmealplanner.profile.web.NutritionGoalItemResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataService {

    private final ActivityLevelRepository activityLevelRepository;
    private final NutritionGoalRepository nutritionGoalRepository;
    private final DietaryPreferenceRepository dietaryPreferenceRepository;
    private final AllergenRepository allergenRepository;

    public ReferenceDataService(
            ActivityLevelRepository activityLevelRepository,
            NutritionGoalRepository nutritionGoalRepository,
            DietaryPreferenceRepository dietaryPreferenceRepository,
            AllergenRepository allergenRepository) {

        this.activityLevelRepository =
                activityLevelRepository;

        this.nutritionGoalRepository =
                nutritionGoalRepository;

        this.dietaryPreferenceRepository =
                dietaryPreferenceRepository;

        this.allergenRepository =
                allergenRepository;
    }

    @Transactional(readOnly = true)
    public List<ActivityLevelItemResponse> getActivityLevels() {

        return activityLevelRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(ReferenceDataService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NutritionGoalItemResponse> getNutritionGoals() {

        return nutritionGoalRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(ReferenceDataService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DietaryPreferenceItemResponse> getDietaryPreferences() {

        return dietaryPreferenceRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(ReferenceDataService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AllergenItemResponse> getAllergens() {

        return allergenRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                .stream()
                .map(ReferenceDataService::toResponse)
                .toList();
    }

    private static ActivityLevelItemResponse toResponse(
            ActivityLevel item) {

        return new ActivityLevelItemResponse(
                item.code(),
                item.displayName(),
                item.description(),
                item.energyFactor(),
                item.displayOrder());
    }

    private static NutritionGoalItemResponse toResponse(
            NutritionGoal item) {

        return new NutritionGoalItemResponse(
                item.code(),
                item.displayName(),
                item.description(),
                item.displayOrder());
    }

    private static DietaryPreferenceItemResponse toResponse(
            DietaryPreference item) {

        return new DietaryPreferenceItemResponse(
                item.code(),
                item.displayName(),
                item.description(),
                item.isExclusionary(),
                item.displayOrder());
    }

    private static AllergenItemResponse toResponse(
            Allergen item) {

        return new AllergenItemResponse(
                item.code(),
                item.displayName(),
                item.description(),
                item.displayOrder());
    }
}
