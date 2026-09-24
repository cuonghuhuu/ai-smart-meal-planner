"""Deterministic Decimal heuristic components for HEURISTIC_MEAL_PLAN_V1."""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

from app.meal_planning.constraints import nutrition_amounts
from app.meal_planning.contracts import (
    EXPECTED_COMPONENT_WEIGHTS,
    MealPlanGenerationRequest,
    RecipeCandidate,
    ScoreComponent,
    ScoreComponentCode,
)
from app.meal_planning.virtual_pantry import PantrySimulation, UnitCatalog

ZERO = Decimal("0")
ONE = Decimal("1")
NEUTRAL = Decimal("0.5")
SCORE_QUANTUM = Decimal("0.0001")
EFFORT_REFERENCE_MINUTES = Decimal("60")


@dataclass(frozen=True, slots=True)
class ScoreBreakdown:
    total: Decimal
    components: tuple[ScoreComponent, ...]

    def value(self, code: ScoreComponentCode) -> Decimal:
        return next(
            component.value for component in self.components
            if component.component_code is code
        )


def score_candidate(
    request: MealPlanGenerationRequest,
    recipe: RecipeCandidate,
    simulation: PantrySimulation,
    previous_uses: int,
    units: UnitCatalog,
) -> ScoreBreakdown:
    """Return seven normalized values and a signed four-place total score."""

    raw = {
        ScoreComponentCode.PANTRY_COVERAGE: simulation.coverage,
        ScoreComponentCode.NUTRITION_FIT: _nutrition_fit(request, recipe, units),
        ScoreComponentCode.EXPIRY_URGENCY: simulation.expiry_urgency,
        ScoreComponentCode.PREFERENCE_MATCH: _preference_match(request, recipe),
        ScoreComponentCode.VARIETY: ONE / (ONE + previous_uses),
        ScoreComponentCode.EFFORT_FIT: _effort_fit(request, recipe),
        ScoreComponentCode.DISLIKE_PENALTY: _dislike_penalty(request, recipe),
    }
    components = tuple(
        ScoreComponent.model_validate({
            "componentCode": code,
            "value": _normalized(raw[code]),
            "weight": EXPECTED_COMPONENT_WEIGHTS[code],
        })
        for code in ScoreComponentCode
    )
    total = sum((
        component.value * component.weight
        * (-ONE if component.component_code is ScoreComponentCode.DISLIKE_PENALTY else ONE)
        for component in components
    ), ZERO)
    total = quantize_score(total)
    return ScoreBreakdown(total, components)


def quantize_score(value: Decimal) -> Decimal:
    """Use the exact four-place representation that ranking and persistence share."""

    return value.quantize(SCORE_QUANTUM, rounding=ROUND_HALF_UP)


def _normalized(value: Decimal) -> Decimal:
    return quantize_score(min(ONE, max(ZERO, value)))


def _nutrition_fit(
    request: MealPlanGenerationRequest, recipe: RecipeCandidate, units: UnitCatalog,
) -> Decimal:
    if recipe.nutrition is None:
        return NEUTRAL
    amounts = nutrition_amounts(recipe, request.planning.default_servings, units)
    known_values = {value.nutrient_code: value for value in recipe.nutrition.values}
    evaluated: list[Decimal] = []
    slots_per_day = Decimal(len(request.planning.requested_meal_slots))
    for target in request.nutrition_targets:
        item = known_values.get(target.nutrient_code)
        if item is None or not units.compatible(item.unit_code, target.unit_code):
            continue
        actual = amounts[target.nutrient_code]
        if target.target_value is not None:
            desired = units.to_base(target.target_value, target.unit_code) / slots_per_day
            if desired == ZERO:
                evaluated.append(ONE if actual == ZERO else ZERO)
            else:
                evaluated.append(max(ZERO, ONE - abs(actual - desired) / desired))
        else:
            minimum = (
                units.to_base(target.min_value, target.unit_code) / slots_per_day
                if target.min_value is not None else None
            )
            maximum = (
                units.to_base(target.max_value, target.unit_code) / slots_per_day
                if target.max_value is not None else None
            )
            if minimum is not None and actual < minimum:
                evaluated.append(max(ZERO, ONE - (minimum - actual) / minimum))
            elif maximum is not None and actual > maximum:
                evaluated.append(
                    ZERO if maximum == ZERO
                    else max(ZERO, ONE - (actual - maximum) / maximum)
                )
            else:
                evaluated.append(ONE)
    if not evaluated:
        return NEUTRAL
    mean_fit = sum(evaluated, ZERO) / len(evaluated)
    completeness = recipe.nutrition.completeness_ratio
    return completeness * mean_fit + (ONE - completeness) * NEUTRAL


def _preference_match(request: MealPlanGenerationRequest, recipe: RecipeCandidate) -> Decimal:
    preferences = request.soft_preferences.preference_codes
    if not preferences:
        return NEUTRAL
    evidence = set(recipe.tag_codes).union(recipe.dietary_codes)
    return Decimal(sum(code in evidence for code in preferences)) / len(preferences)


def _effort_fit(request: MealPlanGenerationRequest, recipe: RecipeCandidate) -> Decimal:
    if recipe.total_minutes is None:
        return NEUTRAL
    reference = Decimal(request.planning.max_minutes_per_meal or EFFORT_REFERENCE_MINUTES)
    return max(ZERO, ONE - Decimal(recipe.total_minutes) / reference)


def _dislike_penalty(request: MealPlanGenerationRequest, recipe: RecipeCandidate) -> Decimal:
    disliked = set(request.soft_preferences.dislike_ingredient_public_ids)
    return Decimal(sum(item.ingredient_public_id in disliked for item in recipe.ingredients)) / len(
        recipe.ingredients
    )
