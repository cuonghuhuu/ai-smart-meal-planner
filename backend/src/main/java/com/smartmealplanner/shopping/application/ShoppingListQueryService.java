package com.smartmealplanner.shopping.application;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartmealplanner.mealplanning.application.PersistedMealPlanQueryService;
import com.smartmealplanner.mealplanning.contract.v1.MealPlanningContractCodes.GenerationStatus;
import com.smartmealplanner.nutrition.application.PlanningUnitQueryService;
import com.smartmealplanner.nutrition.application.PlanningUnitSnapshot;
import com.smartmealplanner.pantry.PantryAvailabilityQueryService;
import com.smartmealplanner.pantry.PantryAvailabilitySnapshot;
import com.smartmealplanner.pantry.PantryExpiryKind;
import com.smartmealplanner.recipe.RecipeRequirementQueryService;
import com.smartmealplanner.recipe.RecipeRequirementSnapshot;
import com.smartmealplanner.shared.application.ReferenceDataIntegrityException;

import org.springframework.stereotype.Service;

/** Deterministic, read-only shopping projection for one persisted meal plan. */
@Service
public class ShoppingListQueryService {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final int OUTPUT_SCALE = 4;

    private final PersistedMealPlanQueryService mealPlans;
    private final RecipeRequirementQueryService recipes;
    private final PantryAvailabilityQueryService pantry;
    private final PlanningUnitQueryService units;

    public ShoppingListQueryService(
            PersistedMealPlanQueryService mealPlans,
            RecipeRequirementQueryService recipes,
            PantryAvailabilityQueryService pantry,
            PlanningUnitQueryService units) {
        this.mealPlans = mealPlans;
        this.recipes = recipes;
        this.pantry = pantry;
        this.units = units;
    }

    public Result get(UUID authenticatedUserPublicId, UUID mealPlanPublicId) {
        PersistedMealPlanQueryService.Detail plan =
                mealPlans.get(authenticatedUserPublicId, mealPlanPublicId);

        Set<UUID> recipeIds = new LinkedHashSet<>();
        plan.entries().forEach(entry -> recipeIds.add(entry.recipePublicId()));
        Map<UUID, RecipeRequirementSnapshot> requirements =
                recipes.requirementsFor(recipeIds);

        Set<UUID> quantifiedIngredientIds = new LinkedHashSet<>();
        Set<String> referencedUnitCodes = new LinkedHashSet<>();
        for (RecipeRequirementSnapshot recipe : requirements.values()) {
            for (RecipeRequirementSnapshot.IngredientRequirement ingredient
                    : recipe.ingredients()) {
                if (!ingredient.optional() && ingredient.quantity() != null) {
                    quantifiedIngredientIds.add(ingredient.ingredientPublicId());
                    referencedUnitCodes.add(ingredient.unitCode());
                }
            }
        }

        List<PantryAvailabilitySnapshot> available =
                pantry.availableFor(authenticatedUserPublicId);
        for (PantryAvailabilitySnapshot lot : available) {
            if (quantifiedIngredientIds.contains(lot.ingredientPublicId())) {
                referencedUnitCodes.add(lot.unitCode());
            }
        }

        Map<String, PlanningUnitSnapshot> definitions = new HashMap<>();
        for (PlanningUnitSnapshot definition
                : units.definitionsFor(referencedUnitCodes)) {
            if (definition == null || definition.unitCode() == null
                    || definition.baseUnitCode() == null
                    || definition.toBaseFactor() == null
                    || definition.toBaseFactor().signum() <= 0
                    || definitions.putIfAbsent(
                    definition.unitCode(), definition) != null) {
                throw inconsistent();
            }
        }
        for (String referencedCode : referencedUnitCodes) {
            if (!definitions.containsKey(referencedCode)) {
                throw inconsistent();
            }
        }

        Map<ItemKey, IngredientMeta> metadata = new HashMap<>();
        Map<DatedItemKey, BigDecimal> datedRequirements = new LinkedHashMap<>();
        Map<UUID, IngredientMeta> unquantified = new LinkedHashMap<>();

        for (PersistedMealPlanQueryService.Entry entry : plan.entries()) {
            RecipeRequirementSnapshot recipe = requirements.get(
                    entry.recipePublicId());
            if (recipe == null || recipe.baseServings() == null
                    || recipe.baseServings().signum() <= 0
                    || entry.servings() == null || entry.servings().signum() <= 0) {
                throw inconsistent();
            }

            BigDecimal servingScale = entry.servings().divide(
                    recipe.baseServings(), CALCULATION_CONTEXT);
            for (RecipeRequirementSnapshot.IngredientRequirement ingredient
                    : recipe.ingredients()) {
                if (ingredient.optional()) {
                    continue;
                }
                IngredientMeta meta = metadata(ingredient);
                if (ingredient.quantity() == null) {
                    if (ingredient.unitCode() != null) {
                        throw inconsistent();
                    }
                    IngredientMeta previous = unquantified.putIfAbsent(
                            ingredient.ingredientPublicId(), meta);
                    requireSameMeta(previous, meta);
                    continue;
                }

                PlanningUnitSnapshot unit = definitions.get(ingredient.unitCode());
                if (unit == null) {
                    throw inconsistent();
                }
                ItemKey key = new ItemKey(
                        ingredient.ingredientPublicId(), unit.baseUnitCode());
                IngredientMeta previous = metadata.putIfAbsent(key, meta);
                requireSameMeta(previous, meta);

                BigDecimal scaled = ingredient.quantity()
                        .multiply(servingScale, CALCULATION_CONTEXT)
                        .multiply(unit.toBaseFactor(), CALCULATION_CONTEXT);
                if (scaled.signum() <= 0) {
                    throw inconsistent();
                }
                datedRequirements.merge(
                        new DatedItemKey(entry.planDate(), key),
                        scaled,
                        BigDecimal::add);
            }
        }

        Map<ItemKey, List<VirtualLot>> lotsByItem = new HashMap<>();
        for (PantryAvailabilitySnapshot lot : available) {
            if (!quantifiedIngredientIds.contains(lot.ingredientPublicId())) {
                continue;
            }
            PlanningUnitSnapshot unit = definitions.get(lot.unitCode());
            if (unit == null || lot.quantityRemaining() == null
                    || lot.quantityRemaining().signum() <= 0) {
                throw inconsistent();
            }
            ItemKey key = new ItemKey(
                    lot.ingredientPublicId(), unit.baseUnitCode());
            if (!metadata.containsKey(key)) {
                continue;
            }
            BigDecimal normalized = lot.quantityRemaining()
                    .multiply(unit.toBaseFactor(), CALCULATION_CONTEXT);
            lotsByItem.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new VirtualLot(
                            lot.pantryItemPublicId(),
                            normalized,
                            lot.expiryDate(),
                            lot.expiryKind()));
        }
        lotsByItem.values().forEach(values -> values.sort(VIRTUAL_LOT_ORDER));

        Map<ItemKey, BigDecimal> requiredTotals = new HashMap<>();
        Map<ItemKey, BigDecimal> coveredTotals = new HashMap<>();
        datedRequirements.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(DATED_ITEM_ORDER))
                .forEach(entry -> {
                    ItemKey item = entry.getKey().item();
                    BigDecimal required = entry.getValue();
                    requiredTotals.merge(item, required, BigDecimal::add);

                    BigDecimal remaining = required;
                    for (VirtualLot lot : lotsByItem.getOrDefault(
                            item, List.of())) {
                        if (remaining.signum() == 0) {
                            break;
                        }
                        if (!lot.usableOn(entry.getKey().date())
                                || lot.remaining().signum() == 0) {
                            continue;
                        }
                        BigDecimal used = remaining.min(lot.remaining());
                        remaining = remaining.subtract(used);
                        lot.consume(used);
                        coveredTotals.merge(item, used, BigDecimal::add);
                    }
                });

        List<Item> items = requiredTotals.entrySet().stream()
                .map(entry -> {
                    IngredientMeta meta = metadata.get(entry.getKey());
                    if (meta == null) {
                        throw inconsistent();
                    }
                    BigDecimal required = entry.getValue();
                    BigDecimal covered = coveredTotals.getOrDefault(
                            entry.getKey(), BigDecimal.ZERO);
                    BigDecimal toBuy = required.subtract(covered);
                    if (toBuy.signum() < 0) {
                        throw inconsistent();
                    }
                    return new Item(
                            entry.getKey().ingredientPublicId(),
                            meta.code(),
                            meta.displayName(),
                            output(required),
                            output(covered),
                            output(toBuy),
                            entry.getKey().baseUnitCode());
                })
                .sorted(ITEM_ORDER)
                .toList();

        List<UnquantifiedItem> unquantifiedItems = unquantified.entrySet()
                .stream()
                .map(entry -> new UnquantifiedItem(
                        entry.getKey(),
                        entry.getValue().code(),
                        entry.getValue().displayName()))
                .sorted(UNQUANTIFIED_ORDER)
                .toList();

        return new Result(
                plan.mealPlanPublicId(),
                plan.status(),
                items,
                unquantifiedItems);
    }

    private static IngredientMeta metadata(
            RecipeRequirementSnapshot.IngredientRequirement ingredient) {
        if (ingredient == null || ingredient.ingredientPublicId() == null
                || ingredient.ingredientCode() == null
                || ingredient.ingredientCode().isBlank()
                || ingredient.ingredientDisplayName() == null
                || ingredient.ingredientDisplayName().isBlank()
                || (ingredient.quantity() == null) != (ingredient.unitCode() == null)
                || (ingredient.quantity() != null
                && ingredient.quantity().signum() <= 0)) {
            throw inconsistent();
        }
        return new IngredientMeta(
                ingredient.ingredientCode(),
                ingredient.ingredientDisplayName());
    }

    private static void requireSameMeta(IngredientMeta previous,
            IngredientMeta current) {
        if (previous != null && !previous.equals(current)) {
            throw inconsistent();
        }
    }

    private static BigDecimal output(BigDecimal value) {
        return value.setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    private static int expiryKindOrder(PantryExpiryKind kind) {
        if (kind == null) {
            throw inconsistent();
        }
        return switch (kind) {
            case USE_BY -> 0;
            case BEST_BEFORE -> 1;
            case UNKNOWN -> 2;
        };
    }

    private static ReferenceDataIntegrityException inconsistent() {
        return new ReferenceDataIntegrityException(
                "Shopping list source data is inconsistent");
    }

    private static final Comparator<VirtualLot> VIRTUAL_LOT_ORDER =
            Comparator.comparing(VirtualLot::expiryDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(lot -> expiryKindOrder(lot.expiryKind()))
                    .thenComparing(lot -> lot.publicId().toString());

    private static final Comparator<DatedItemKey> DATED_ITEM_ORDER =
            Comparator.comparing(DatedItemKey::date)
                    .thenComparing(key -> key.item().ingredientPublicId().toString())
                    .thenComparing(key -> key.item().baseUnitCode());

    private static final Comparator<Item> ITEM_ORDER =
            Comparator.comparing(Item::ingredientDisplayName,
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Item::ingredientCode)
                    .thenComparing(Item::unitCode);

    private static final Comparator<UnquantifiedItem> UNQUANTIFIED_ORDER =
            Comparator.comparing(UnquantifiedItem::ingredientDisplayName,
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(UnquantifiedItem::ingredientCode);

    public record Result(
            UUID mealPlanPublicId,
            GenerationStatus status,
            List<Item> items,
            List<UnquantifiedItem> unquantifiedItems) {
        public Result {
            items = List.copyOf(items);
            unquantifiedItems = List.copyOf(unquantifiedItems);
        }
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

    private record ItemKey(UUID ingredientPublicId, String baseUnitCode) {
    }

    private record DatedItemKey(LocalDate date, ItemKey item) {
    }

    private record IngredientMeta(String code, String displayName) {
    }

    private static final class VirtualLot {
        private final UUID publicId;
        private BigDecimal remaining;
        private final LocalDate expiryDate;
        private final PantryExpiryKind expiryKind;

        private VirtualLot(UUID publicId, BigDecimal remaining,
                LocalDate expiryDate, PantryExpiryKind expiryKind) {
            if (publicId == null || remaining == null || remaining.signum() <= 0
                    || expiryKind == null) {
                throw inconsistent();
            }
            this.publicId = publicId;
            this.remaining = remaining;
            this.expiryDate = expiryDate;
            this.expiryKind = expiryKind;
        }

        UUID publicId() {
            return publicId;
        }

        BigDecimal remaining() {
            return remaining;
        }

        LocalDate expiryDate() {
            return expiryDate;
        }

        PantryExpiryKind expiryKind() {
            return expiryKind;
        }

        boolean usableOn(LocalDate date) {
            return expiryDate == null || !expiryDate.isBefore(date);
        }

        void consume(BigDecimal quantity) {
            remaining = remaining.subtract(quantity);
            if (remaining.signum() < 0) {
                throw inconsistent();
            }
        }
    }
}
