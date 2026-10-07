"""Seven-component Decimal scoring for HEURISTIC_RECIPE_RANK_V1."""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

from app.meal_planning.contracts import (
    EXPECTED_COMPONENT_WEIGHTS,
    RecipeCandidate,
    ScoreComponentCode,
)
from app.meal_planning.virtual_pantry import PantrySimulation, UnitCatalog
from app.recipe_ranking.contracts import RankingScoreComponent, RecipeRankingRequest

ZERO = Decimal("0")
ONE = Decimal("1")
NEUTRAL = Decimal("0.5")
QUANTUM = Decimal("0.0001")
EFFORT_REFERENCE = Decimal("60")
SLOT_SHARES = {
    "BREAKFAST": Decimal("0.25"),
    "MORNING_SNACK": Decimal("0.05"),
    "LUNCH": Decimal("0.30"),
    "AFTERNOON_SNACK": Decimal("0.05"),
    "DINNER": Decimal("0.30"),
    "EVENING_SNACK": Decimal("0.05"),
}


@dataclass(frozen=True, slots=True)
class ScoreBreakdown:
    total: Decimal
    components: tuple[RankingScoreComponent, ...]

    def value(self, code: ScoreComponentCode) -> Decimal:
        return next(v.value for v in self.components if v.component_code is code)


def score_candidate(
    request: RecipeRankingRequest,
    recipe: RecipeCandidate,
    simulation: PantrySimulation,
    recent_count: int,
    units: UnitCatalog,
) -> ScoreBreakdown:
    raw = {
        ScoreComponentCode.PANTRY_COVERAGE: simulation.coverage,
        ScoreComponentCode.NUTRITION_FIT: _nutrition_fit(request, recipe, units),
        ScoreComponentCode.EXPIRY_URGENCY: simulation.expiry_urgency,
        ScoreComponentCode.PREFERENCE_MATCH: _preference_match(request, recipe),
        ScoreComponentCode.VARIETY: ONE / (ONE + Decimal(recent_count)),
        ScoreComponentCode.EFFORT_FIT: _effort_fit(request, recipe),
        ScoreComponentCode.DISLIKE_PENALTY: _dislike_penalty(request, recipe),
    }
    components = tuple(
        RankingScoreComponent.model_validate({
            "componentCode": code,
            "value": _normalized(raw[code]),
            "weight": EXPECTED_COMPONENT_WEIGHTS[code],
        })
        for code in ScoreComponentCode
    )
    total = sum(
        (
            v.value * v.weight *
            (-ONE if v.component_code is ScoreComponentCode.DISLIKE_PENALTY else ONE)
            for v in components
        ),
        ZERO,
    )
    return ScoreBreakdown(_q(total), components)


def _q(value: Decimal) -> Decimal:
    return value.quantize(QUANTUM, rounding=ROUND_HALF_UP)


def _normalized(value: Decimal) -> Decimal:
    return _q(min(ONE, max(ZERO, value)))


def _nutrition_fit(
    request: RecipeRankingRequest,
    recipe: RecipeCandidate,
    units: UnitCatalog,
) -> Decimal:
    if recipe.nutrition is None or not request.nutrition_targets:
        return NEUTRAL
    by_code = {v.nutrient_code: v for v in recipe.nutrition.values}
    share = SLOT_SHARES[request.context.meal_slot_code.value]
    evaluated: list[Decimal] = []
    for target in request.nutrition_targets:
        item = by_code.get(target.nutrient_code)
        if item is None or not units.compatible(item.unit_code, target.unit_code):
            continue
        actual = units.to_base(
            item.amount_per_serving * request.context.servings, item.unit_code
        )
        if target.target_value is not None:
            desired = units.to_base(target.target_value, target.unit_code) * share
            evaluated.append(
                ONE if desired == ZERO and actual == ZERO
                else ZERO if desired == ZERO
                else max(ZERO, ONE - abs(actual - desired) / desired)
            )
            continue
        minimum = (
            units.to_base(target.min_value, target.unit_code) * share
            if target.min_value is not None else None
        )
        maximum = (
            units.to_base(target.max_value, target.unit_code) * share
            if target.max_value is not None else None
        )
        if minimum is not None and actual < minimum:
            evaluated.append(
                ZERO if minimum == ZERO
                else max(ZERO, ONE - (minimum - actual) / minimum)
            )
        elif maximum is not None and actual > maximum:
            evaluated.append(
                ZERO if maximum == ZERO
                else max(ZERO, ONE - (actual - maximum) / maximum)
            )
        else:
            evaluated.append(ONE)

    if not evaluated:
        return NEUTRAL
    mean = sum(evaluated, ZERO) / Decimal(len(evaluated))
    completeness = recipe.nutrition.completeness_ratio
    return completeness * mean + (ONE - completeness) * NEUTRAL


def _preference_match(request: RecipeRankingRequest, recipe: RecipeCandidate) -> Decimal:
    preferences = request.soft_preferences.preference_codes
    if not preferences:
        return NEUTRAL
    evidence = set(recipe.dietary_codes).union(recipe.tag_codes)
    return Decimal(sum(v in evidence for v in preferences)) / Decimal(len(preferences))


def _effort_fit(request: RecipeRankingRequest, recipe: RecipeCandidate) -> Decimal:
    if recipe.total_minutes is None:
        return NEUTRAL
    reference = Decimal(request.context.max_minutes or EFFORT_REFERENCE)
    return max(ZERO, ONE - Decimal(recipe.total_minutes) / reference)


def _dislike_penalty(request: RecipeRankingRequest, recipe: RecipeCandidate) -> Decimal:
    disliked = set(request.soft_preferences.dislike_ingredient_public_ids)
    return Decimal(
        sum(v.ingredient_public_id in disliked for v in recipe.ingredients)
    ) / Decimal(len(recipe.ingredients))
