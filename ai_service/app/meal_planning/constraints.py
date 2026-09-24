"""Fail-closed CSP domains and hard daily-nutrition forward checking."""

from __future__ import annotations

from decimal import Decimal
from typing import Mapping
from uuid import UUID

from app.meal_planning.contracts import (
    AllergenEvidenceStatus,
    MealPlanGenerationRequest,
    MealSlotCode,
    NutritionTarget,
    RecipeCandidate,
    UnfilledSlotReasonCode,
)
from app.meal_planning.virtual_pantry import UnitCatalog

# V1 seed-data exclusionary vocabulary. Exact recipe dietary codes are positive
# evidence supplied by Java; a tag or an inferred implication is insufficient.
SUPPORTED_EXCLUSIONARY_DIETS = frozenset({
    "VEGETARIAN", "VEGAN", "PESCATARIAN", "HALAL", "KOSHER", "GLUTEN_FREE", "DAIRY_FREE",
})


def unsupported_hard_constraint(request: MealPlanGenerationRequest) -> bool:
    if set(request.hard_constraints.exclusionary_dietary_codes) - SUPPORTED_EXCLUSIONARY_DIETS:
        return True
    return any(
        target.hard_limit and target.min_value is None and target.max_value is None
        for target in request.nutrition_targets
    )


def candidate_rejection(
    request: MealPlanGenerationRequest,
    recipe: RecipeCandidate,
    slot: MealSlotCode,
    units: UnitCatalog | None = None,
    allergen_evidence: Mapping[UUID, Mapping[str, AllergenEvidenceStatus]] | None = None,
) -> UnfilledSlotReasonCode | None:
    """Static hard constraints; no heuristic or Pantry consumption here."""

    if slot not in recipe.meal_slot_codes:
        return UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE
    limit = request.planning.max_minutes_per_meal
    if limit is not None and (recipe.total_minutes is None or recipe.total_minutes > limit):
        return UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    if not set(request.hard_constraints.exclusionary_dietary_codes).issubset(recipe.dietary_codes):
        return UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    avoid = set(request.hard_constraints.avoid_ingredient_public_ids)
    if any(ingredient.ingredient_public_id in avoid for ingredient in recipe.ingredients):
        return UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    facts = allergen_evidence if allergen_evidence is not None else _allergen_index(request)
    if any(
        facts.get(ingredient.ingredient_public_id, {}).get(
            allergen, AllergenEvidenceStatus.UNKNOWN
        ) is not AllergenEvidenceStatus.FREE_FROM
        for ingredient in recipe.ingredients
        for allergen in request.hard_constraints.allergen_codes
    ):
        return UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    if any(
        not ingredient.optional and (ingredient.quantity is None or ingredient.unit_code is None)
        for ingredient in recipe.ingredients
    ):
        return UnfilledSlotReasonCode.PANTRY_INFEASIBLE
    resolved_units = units or UnitCatalog.from_request(request)
    if any(
        not _hard_nutrition_known(recipe, target, resolved_units)
        for target in request.nutrition_targets if target.hard_limit
    ):
        return UnfilledSlotReasonCode.NUTRITION_INFEASIBLE
    return None


def _hard_nutrition_known(
    recipe: RecipeCandidate, target: NutritionTarget, units: UnitCatalog,
) -> bool:
    return (
        recipe.nutrition is not None
        and recipe.nutrition.completeness_ratio == Decimal("1")
        and any(
            value.nutrient_code == target.nutrient_code
            and units.compatible(value.unit_code, target.unit_code)
            for value in recipe.nutrition.values
        )
    )


def domain_for_slot(
    request: MealPlanGenerationRequest,
    slot: MealSlotCode,
) -> tuple[tuple[RecipeCandidate, ...], UnfilledSlotReasonCode]:
    accepted: list[RecipeCandidate] = []
    rejected: set[UnfilledSlotReasonCode] = set()
    units = UnitCatalog.from_request(request)
    evidence = _allergen_index(request)
    for recipe in sorted(request.recipe_candidates, key=lambda item: str(item.recipe_public_id)):
        reason = candidate_rejection(request, recipe, slot, units, evidence)
        if reason is None:
            accepted.append(recipe)
        else:
            rejected.add(reason)
    if accepted:
        return tuple(accepted), UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE
    for reason in (
        UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT,
        UnfilledSlotReasonCode.PANTRY_INFEASIBLE,
        UnfilledSlotReasonCode.NUTRITION_INFEASIBLE,
    ):
        if reason in rejected:
            return (), reason
    return (), UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE


def _allergen_index(
    request: MealPlanGenerationRequest,
) -> dict[UUID, dict[str, AllergenEvidenceStatus]]:
    return {
        ingredient.ingredient_public_id: {
            fact.allergen_code: fact.evidence_status for fact in ingredient.allergen_facts
        }
        for ingredient in request.ingredient_facts
    }


def nutrition_amounts(
    recipe: RecipeCandidate, servings: Decimal, units: UnitCatalog,
) -> dict[str, Decimal]:
    if recipe.nutrition is None:
        return {}
    return {
        value.nutrient_code: units.to_base(value.amount_per_serving * servings, value.unit_code)
        for value in recipe.nutrition.values
    }


def hard_maximums_hold(
    request: MealPlanGenerationRequest,
    totals: Mapping[str, Decimal],
    units: UnitCatalog,
) -> bool:
    for target in request.nutrition_targets:
        if target.hard_limit and target.max_value is not None:
            maximum = units.to_base(target.max_value, target.unit_code)
            amount = totals.get(target.nutrient_code)
            if amount is None or amount > maximum:
                return False
    return True


def hard_minimums_remain_possible(
    request: MealPlanGenerationRequest,
    totals: Mapping[str, Decimal],
    remaining_slots: tuple[MealSlotCode, ...],
    optimistic_maximums: Mapping[MealSlotCode, Mapping[str, Decimal]],
    units: UnitCatalog,
) -> bool:
    """Optimistic bound: prune only if even best independent future slots fall short."""

    for target in request.nutrition_targets:
        if not target.hard_limit or target.min_value is None:
            continue
        possible = totals.get(target.nutrient_code, Decimal("0"))
        for slot in remaining_slots:
            possible += optimistic_maximums[slot].get(target.nutrient_code, Decimal("0"))
        if possible < units.to_base(target.min_value, target.unit_code):
            return False
    return True
