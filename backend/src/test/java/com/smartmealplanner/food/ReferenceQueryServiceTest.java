package com.smartmealplanner.food;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceQueryServiceTest {

    @Mock
    private IngredientRepository ingredients;

    @Mock
    private FoodRepository foods;

    @Mock
    private IngredientFoodRepository ingredientFoods;

    @Test
    void activeIngredientLookupUsesThePreciseIntegrityFailure() {
        Ingredient malformed = mock(Ingredient.class);
        when(ingredients.findActiveByPublicId(any(byte[].class)))
                .thenReturn(Optional.of(malformed));

        assertThatThrownBy(() -> new IngredientReferenceQueryService(ingredients)
                .resolveActiveByPublicId(UUID.randomUUID()))
                .isExactlyInstanceOf(ReferenceDataIntegrityException.class);
    }

    @Test
    void foodLookupUsesThePreciseIntegrityFailure() {
        Food malformed = mock(Food.class);
        when(foods.findByPublicId(any(byte[].class)))
                .thenReturn(Optional.of(malformed));

        assertThatThrownBy(() -> new FoodReferenceQueryService(
                foods,
                ingredientFoods).resolveByPublicId(UUID.randomUUID()))
                .isExactlyInstanceOf(ReferenceDataIntegrityException.class);
    }
}
