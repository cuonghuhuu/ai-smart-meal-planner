"""Bounded, deterministic, non-sensitive recommendation explanations."""

from __future__ import annotations

from app.meal_planning.contract_limits import MAX_EXPLANATION_LENGTH
from app.meal_planning.contracts import ScoreComponentCode, UnfilledSlotReasonCode
from app.meal_planning.scoring import ScoreBreakdown
from app.meal_planning.virtual_pantry import PantrySimulation

_REASONS = {
    UnfilledSlotReasonCode.NO_ELIGIBLE_RECIPE: "No recipe is eligible for this meal slot.",
    UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT: (
        "No recipe satisfies the hard safety and dietary rules."
    ),
    UnfilledSlotReasonCode.UNSUPPORTED_HARD_CONSTRAINT: (
        "A requested hard rule cannot be enforced safely."
    ),
    UnfilledSlotReasonCode.PANTRY_INFEASIBLE: (
        "Ingredient quantities or units cannot be simulated safely."
    ),
    UnfilledSlotReasonCode.NUTRITION_INFEASIBLE: (
        "No candidate can satisfy the hard daily nutrition bounds."
    ),
    UnfilledSlotReasonCode.SEARCH_LIMIT_REACHED: "The bounded search stopped before this slot.",
}


def explain_entry(
    score: ScoreBreakdown, simulation: PantrySimulation, previous_uses: int,
) -> str:
    coverage = score.value(ScoreComponentCode.PANTRY_COVERAGE) * 100
    nutrition = score.value(ScoreComponentCode.NUTRITION_FIT) * 100
    parts = [
        f"Pantry coverage {coverage:.0f}%",
        f"nutrition fit {nutrition:.0f}%",
        f"{simulation.missing_ingredients} ingredient shortfall(s)",
    ]
    if score.value(ScoreComponentCode.EXPIRY_URGENCY) > 0:
        parts.append("uses soon-expiring stock")
    if previous_uses:
        parts.append("repeated recipe")
    if score.value(ScoreComponentCode.DISLIKE_PENALTY) > 0:
        parts.append("includes a disliked ingredient")
    return ("; ".join(parts) + ".")[:MAX_EXPLANATION_LENGTH]


def explain_unfilled(reason: UnfilledSlotReasonCode) -> str:
    return _REASONS[reason]
