package com.smartmealplanner.shopping.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.shopping.application.ShoppingListQueryService.Result;

/** Public representation of the current shopping projection for a meal plan. */
public record ShoppingListResponse(
        UUID mealPlanPublicId,
        GenerationStatus status,
        List<Item> items,
        List<UnquantifiedItem> unquantifiedItems) {

    static ShoppingListResponse from(Result result) {
        return new ShoppingListResponse(
                result.mealPlanPublicId(),
                result.status(),
                result.items().stream()
                        .map(item -> new Item(
                                item.ingredientPublicId(),
                                item.ingredientCode(),
                                item.ingredientDisplayName(),
                                item.requiredQuantity(),
                                item.pantryCoveredQuantity(),
                                item.quantityToBuy(),
                                item.unitCode()))
                        .toList(),
                result.unquantifiedItems().stream()
                        .map(item -> new UnquantifiedItem(
                                item.ingredientPublicId(),
                                item.ingredientCode(),
                                item.ingredientDisplayName()))
                        .toList());
    }

    public record Item(
            UUID ingredientPublicId,
            String ingredientCode,
            String ingredientDisplayName,
            BigDecimal requiredQuantity,
            BigDecimal pantryCoveredQuantity,
            BigDecimal quantityToBuy,
            String unitCode) {
    }

    public record UnquantifiedItem(
            UUID ingredientPublicId,
            String ingredientCode,
            String ingredientDisplayName) {
    }
}
