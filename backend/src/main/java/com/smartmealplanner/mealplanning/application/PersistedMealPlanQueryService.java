package com.smartmealplanner.mealplanning.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.MealSlotCode;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.UnfilledSlotReasonCode;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads a persisted generation outcome for its authenticated owner. */
@Service
public class PersistedMealPlanQueryService {
    private final CurrentUserService currentUserService;
    private final MealPlanReadPort plans;

    public PersistedMealPlanQueryService(CurrentUserService currentUserService,
            MealPlanReadPort plans) {
        this.currentUserService = currentUserService;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public Detail get(UUID authenticatedUserPublicId, UUID mealPlanPublicId) {
        Long ownerId = currentUserService.getIdentity(authenticatedUserPublicId)
                .internalId();
        var header = plans.findOwnedPlan(mealPlanPublicId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Meal plan not found"));

        List<Entry> entries = plans.findEntries(header.internalId()).stream()
                .map(entry -> new Entry(entry.planDate(), entry.mealSlotCode(),
                        entry.recipePublicId(), entry.recipeTitle(), entry.servings()))
                .toList();
        List<UnfilledSlot> unfilledSlots = plans.findUnfilledSlots(header.internalId())
                .stream()
                .map(slot -> new UnfilledSlot(slot.planDate(), slot.mealSlotCode(),
                        slot.reasonCode(), slot.explanation()))
                .toList();

        return new Detail(header.mealPlanPublicId(), header.requestPublicId(),
                header.status(), header.startDate(), header.endDate(),
                header.defaultServings(), entries, unfilledSlots);
    }

    public record Detail(UUID mealPlanPublicId, UUID requestPublicId,
            GenerationStatus status, LocalDate startDate, LocalDate endDate,
            BigDecimal defaultServings, List<Entry> entries,
            List<UnfilledSlot> unfilledSlots) {
        public Detail {
            entries = List.copyOf(entries);
            unfilledSlots = List.copyOf(unfilledSlots);
        }
    }

    public record Entry(LocalDate planDate, MealSlotCode mealSlotCode,
            UUID recipePublicId, String recipeTitle, BigDecimal servings) {
    }

    public record UnfilledSlot(LocalDate planDate, MealSlotCode mealSlotCode,
            UnfilledSlotReasonCode reasonCode, String explanation) {
    }
}
