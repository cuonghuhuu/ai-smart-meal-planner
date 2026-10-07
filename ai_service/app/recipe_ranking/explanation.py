"""Bounded deterministic explanation text for Recipe Ranking V1."""

from app.meal_planning.contracts import ScoreComponentCode
from app.meal_planning.virtual_pantry import PantrySimulation
from app.recipe_ranking.contract_limits import MAX_EXPLANATION_LENGTH
from app.recipe_ranking.scoring import ScoreBreakdown


def explain_ranked_recipe(
    score: ScoreBreakdown,
    simulation: PantrySimulation,
    recent_count: int,
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
    if score.value(ScoreComponentCode.PREFERENCE_MATCH) > 0.5:
        parts.append("matches declared preferences")
    if recent_count:
        parts.append(f"recommended {recent_count} time(s) recently")
    if score.value(ScoreComponentCode.DISLIKE_PENALTY) > 0:
        parts.append("includes a disliked ingredient")
    return ("; ".join(parts) + ".")[:MAX_EXPLANATION_LENGTH]
