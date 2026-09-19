package com.smartmealplanner.pantry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.food.FoodReferenceQueryService;
import com.smartmealplanner.food.FoodReferenceSnapshot;
import com.smartmealplanner.food.IngredientReferenceQueryService;
import com.smartmealplanner.food.IngredientReferenceSnapshot;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceQueryService;
import com.smartmealplanner.nutrition.application.MeasurementUnitReferenceSnapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pantry-owned read boundary for future recommendation and meal planning code. */
@Service
public class PantryAvailabilityQueryService {

    private final PantryItemRepository items;
    private final CurrentUserService currentUserService;
    private final IngredientReferenceQueryService ingredientReferences;
    private final FoodReferenceQueryService foodReferences;
    private final MeasurementUnitReferenceQueryService unitReferences;

    public PantryAvailabilityQueryService(
            PantryItemRepository items,
            CurrentUserService currentUserService,
            IngredientReferenceQueryService ingredientReferences,
            FoodReferenceQueryService foodReferences,
            MeasurementUnitReferenceQueryService unitReferences) {
        this.items = items;
        this.currentUserService = currentUserService;
        this.ingredientReferences = ingredientReferences;
        this.foodReferences = foodReferences;
        this.unitReferences = unitReferences;
    }

    @Transactional(readOnly = true)
    public List<PantryAvailabilitySnapshot> availableFor(
            UUID authenticatedPublicId) {

        CurrentUserIdentity identity = currentUserService.getIdentity(
                authenticatedPublicId);
        List<PantryItem> values = items.findAvailableByUserId(
                identity.internalId(),
                PantryItemStatus.AVAILABLE);
        if (values.isEmpty()) {
            return List.of();
        }

        Set<Long> ingredientIds = new HashSet<>();
        Set<Long> foodIds = new HashSet<>();
        Set<Long> unitIds = new HashSet<>();
        for (PantryItem item : values) {
            ingredientIds.add(item.ingredientId());
            if (item.foodId() != null) {
                foodIds.add(item.foodId());
            }
            unitIds.add(item.unitId());
        }

        Map<Long, IngredientReferenceSnapshot> ingredientsById =
                ingredientReferences.resolveByInternalIds(ingredientIds);
        Map<Long, FoodReferenceSnapshot> foodsById = foodIds.isEmpty()
                ? Map.of()
                : foodReferences.resolveByInternalIds(foodIds);
        Map<Long, MeasurementUnitReferenceSnapshot> unitsById =
                unitReferences.resolveByInternalIds(unitIds);
        if (ingredientsById.size() != ingredientIds.size()
                || foodsById.size() != foodIds.size()
                || unitsById.size() != unitIds.size()) {
            throw new PantryException(PantryFailure.CORRUPTED_PANTRY_DATA);
        }

        return values.stream()
                .map(item -> {
                    IngredientReferenceSnapshot ingredient = ingredientsById.get(
                            item.ingredientId());
                    FoodReferenceSnapshot food = item.foodId() == null
                            ? null
                            : foodsById.get(item.foodId());
                    MeasurementUnitReferenceSnapshot unit = unitsById.get(
                            item.unitId());
                    if (ingredient == null || unit == null
                            || (item.foodId() != null && food == null)) {
                        throw new PantryException(
                                PantryFailure.CORRUPTED_PANTRY_DATA);
                    }
                    return new PantryAvailabilitySnapshot(
                            item.publicId(),
                            ingredient.publicId(),
                            food == null ? null : food.publicId(),
                            item.quantityRemaining(),
                            unit.code(),
                            item.expiryDate(),
                            item.expiryKind(),
                            item.storageLocation());
                })
                .toList();
    }
}
