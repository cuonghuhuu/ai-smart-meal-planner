package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private ActivityLevelRepository activityLevelRepository;

    @Mock
    private NutritionGoalRepository nutritionGoalRepository;

    @Mock
    private DietaryPreferenceRepository dietaryPreferenceRepository;

    @Mock
    private AllergenRepository allergenRepository;

    private ReferenceDataService service;

    @BeforeEach
    void setUp() {
        service = new ReferenceDataService(
                activityLevelRepository,
                nutritionGoalRepository,
                dietaryPreferenceRepository,
                allergenRepository);
    }

    @Test
    void getActivityLevelsReturnsMappedInOrder() {
        ActivityLevel a1 = new ActivityLevel("SEDENTARY", "Sedentary", "Desc1", BigDecimal.valueOf(1.2), (short) 10);
        ActivityLevel a2 = new ActivityLevel("LIGHT", "Lightly active", "Desc2", BigDecimal.valueOf(1.375), (short) 20);

        when(activityLevelRepository.findAllByOrderByDisplayOrderAscCodeAsc())
                .thenReturn(List.of(a1, a2));

        List<ActivityLevelItemResponse> list = service.getActivityLevels();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).code()).isEqualTo("SEDENTARY");
        assertThat(list.get(0).energyFactor()).isEqualTo(BigDecimal.valueOf(1.2));
        assertThat(list.get(1).code()).isEqualTo("LIGHT");
    }

    @Test
    void getNutritionGoalsReturnsMappedInOrder() {
        NutritionGoal g1 = new NutritionGoal("LOSE_WEIGHT", "Lose weight", "Desc1", (short) 10);
        NutritionGoal g2 = new NutritionGoal("MAINTAIN", "Maintain weight", "Desc2", (short) 20);

        when(nutritionGoalRepository.findAllByOrderByDisplayOrderAscCodeAsc())
                .thenReturn(List.of(g1, g2));

        List<NutritionGoalItemResponse> list = service.getNutritionGoals();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).code()).isEqualTo("LOSE_WEIGHT");
        assertThat(list.get(1).code()).isEqualTo("MAINTAIN");
    }

    @Test
    void getDietaryPreferencesReturnsMappedInOrder() {
        DietaryPreference p1 = new DietaryPreference("VEGETARIAN", "Vegetarian", "Desc1", true, (short) 10);
        DietaryPreference p2 = new DietaryPreference("LOW_CARB", "Low carb", "Desc2", false, (short) 80);

        when(dietaryPreferenceRepository.findAllByOrderByDisplayOrderAscCodeAsc())
                .thenReturn(List.of(p1, p2));

        List<DietaryPreferenceItemResponse> list = service.getDietaryPreferences();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).code()).isEqualTo("VEGETARIAN");
        assertThat(list.get(0).isExclusionary()).isTrue();
        assertThat(list.get(1).code()).isEqualTo("LOW_CARB");
        assertThat(list.get(1).isExclusionary()).isFalse();
    }

    @Test
    void getAllergensReturnsMappedInOrder() {
        Allergen al1 = new Allergen("GLUTEN", "Cereals containing gluten", "Desc1", (short) 10);
        Allergen al2 = new Allergen("PEANUT", "Peanuts", "Desc2", (short) 50);

        when(allergenRepository.findAllByOrderByDisplayOrderAscCodeAsc())
                .thenReturn(List.of(al1, al2));

        List<AllergenItemResponse> list = service.getAllergens();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).code()).isEqualTo("GLUTEN");
        assertThat(list.get(1).code()).isEqualTo("PEANUT");
    }
}
