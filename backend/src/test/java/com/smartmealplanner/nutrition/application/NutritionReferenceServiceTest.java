package com.smartmealplanner.nutrition.application;

import java.util.List;

import com.smartmealplanner.nutrition.persistence.MeasurementUnit;
import com.smartmealplanner.nutrition.persistence.MeasurementUnitType;
import com.smartmealplanner.nutrition.persistence.Nutrient;
import com.smartmealplanner.nutrition.persistence.NutrientKind;
import com.smartmealplanner.nutrition.persistence.NutrientRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionReferenceServiceTest {

    @Mock
    private NutrientRepository nutrientRepository;

    private NutritionReferenceService service;

    @BeforeEach
    void setUp() {
        service = new NutritionReferenceService(nutrientRepository);
    }

    @Test
    void getNutrientsPreservesRepositoryOrderAndExposesStableCodes() {
        MeasurementUnit grams = new MeasurementUnit(
                "g",
                "gram",
                MeasurementUnitType.MASS,
                null,
                null);
        MeasurementUnit kcal = new MeasurementUnit(
                "kcal",
                "kilocalorie",
                MeasurementUnitType.ENERGY,
                null,
                null);
        Nutrient energy = new Nutrient(
                "ENERGY",
                "Energy",
                kcal,
                NutrientKind.ENERGY,
                true,
                (short) 10);
        Nutrient protein = new Nutrient(
                "PROTEIN",
                "Protein",
                grams,
                NutrientKind.MACRONUTRIENT,
                true,
                (short) 20);

        when(nutrientRepository.findAllWithUnitOrderByDisplayOrderAscCodeAsc())
                .thenReturn(List.of(energy, protein));

        List<NutritionReferenceNutrientView> result = service.getNutrients();

        assertThat(result)
                .extracting(NutritionReferenceNutrientView::code)
                .containsExactly("ENERGY", "PROTEIN");
        assertThat(result.get(0).displayName()).isEqualTo("Energy");
        assertThat(result.get(0).nutrientKind())
                .isEqualTo(NutrientKind.ENERGY);
        assertThat(result.get(0).core()).isTrue();
        assertThat(result.get(0).displayOrder()).isEqualTo((short) 10);
        assertThat(result.get(0).unitCode()).isEqualTo("kcal");
        assertThat(result.get(0).unitDisplayName())
                .isEqualTo("kilocalorie");
        assertThat(result.get(1).unitCode()).isEqualTo("g");

        assertThat(NutritionReferenceNutrientView.class
                .getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("id", "nutrientId", "unitId");
        verify(nutrientRepository)
                .findAllWithUnitOrderByDisplayOrderAscCodeAsc();
    }
}
