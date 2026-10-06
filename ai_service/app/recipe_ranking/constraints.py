"""Fail-closed hard constraints for single-Recipe ranking."""

from __future__ import annotations

from decimal import Decimal
from typing import Mapping
from uuid import UUID

from app.meal_planning.contracts import AllergenEvidenceStatus, RecipeCandidate
from app.meal_planning.virtual_pantry import UnitCatalog
from app.recipe_ranking.contracts import RecipeRankingRequest

SUPPORTED_EXCLUSIONARY_DIETS = frozenset({
    "VEGETARIAN", "VEGAN", "PESCATARIAN", "HALAL", "KOSHER",
    "GLUTEN_FREE", "DAIRY_FREE",
})


def unsupported_hard_constraint(request: RecipeRankingRequest) -> bool:
    return bool(set(request.hard_constraints.exclusionary_dietary_codes)
                - SUPPORTED_EXCLUSIONARY_DIETS)


def allergen_index(
    request: RecipeRankingRequest,
) -> dict[UUID, dict[str, AllergenEvidenceStatus]]:
    return {
        item.ingredient_public_id: {
            fact.allergen_code: fact.evidence_status for fact in item.allergen_facts
        }
        for item in request.ingredient_facts
    }


def passes_non_nutrition(
    request: RecipeRankingRequest,
    recipe: RecipeCandidate,
    evidence: Mapping[UUID, Mapping[str, AllergenEvidenceStatus]],
) -> bool:
    if request.context.meal_slot_code not in recipe.meal_slot_codes:
        return False
    limit = request.context.max_minutes
    if limit is not None and (recipe.total_minutes is None or recipe.total_minutes > limit):
        return False
    if not set(request.hard_constraints.exclusionary_dietary_codes).issubset(
        recipe.dietary_codes
    ):
        return False
    avoided = set(request.hard_constraints.avoid_ingredient_public_ids)
    if any(v.ingredient_public_id in avoided for v in recipe.ingredients):
        return False
    if any(
        evidence.get(v.ingredient_public_id, {}).get(
            allergen, AllergenEvidenceStatus.UNKNOWN
        ) is not AllergenEvidenceStatus.FREE_FROM
        for v in recipe.ingredients
        for allergen in request.hard_constraints.allergen_codes
    ):
        return False
    return not any(
        not v.optional and (v.quantity is None or v.unit_code is None)
        for v in recipe.ingredients
    )


def passes_hard_nutrition_maxima(
    request: RecipeRankingRequest,
    recipe: RecipeCandidate,
    units: UnitCatalog,
) -> bool:
    for target in request.nutrition_targets:
        if not target.hard_limit or target.max_value is None:
            continue
        if recipe.nutrition is None or recipe.nutrition.completeness_ratio != Decimal("1"):
            return False
        value = next(
            (
                v for v in recipe.nutrition.values
                if v.nutrient_code == target.nutrient_code
                and units.compatible(v.unit_code, target.unit_code)
            ),
            None,
        )
        if value is None:
            return False
        actual = units.to_base(
            value.amount_per_serving * request.context.servings, value.unit_code
        )
        if actual > units.to_base(target.max_value, target.unit_code):
            return False
    return True
