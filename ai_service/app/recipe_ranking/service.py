"""Stateless deterministic Personalized Recipe Ranking V1 orchestration."""

from decimal import ROUND_HALF_UP, localcontext

from app.meal_planning.virtual_pantry import UnitCatalog, VirtualPantry
from app.recipe_ranking.constraints import (
    allergen_index,
    passes_hard_nutrition_maxima,
    passes_non_nutrition,
    unsupported_hard_constraint,
)
from app.recipe_ranking.contracts import (
    InfeasibleReason,
    RankedRecipe,
    RankingStatus,
    RecipeRankingRequest,
    RecipeRankingResponse,
)
from app.recipe_ranking.explanation import explain_ranked_recipe
from app.recipe_ranking.scoring import score_candidate


def rank_recipes(request: RecipeRankingRequest) -> RecipeRankingResponse:
    with localcontext() as context:
        context.prec = 64
        context.rounding = ROUND_HALF_UP
        return _rank(request)


def _rank(request: RecipeRankingRequest) -> RecipeRankingResponse:
    if unsupported_hard_constraint(request):
        return _infeasible(request, InfeasibleReason.UNSUPPORTED_HARD_CONSTRAINT)

    units = UnitCatalog.from_request(request)
    pantry = VirtualPantry.from_request(request)
    evidence = allergen_index(request)
    recent = {v.recipe_public_id: v.count for v in request.recent_recipe_counts}
    scored: list[RankedRecipe] = []
    passed_non_nutrition = 0
    nutrition_rejected = 0

    for recipe in sorted(request.recipe_candidates, key=lambda v: str(v.recipe_public_id)):
        if not passes_non_nutrition(request, recipe, evidence):
            continue
        passed_non_nutrition += 1
        if not passes_hard_nutrition_maxima(request, recipe, units):
            nutrition_rejected += 1
            continue

        simulation = pantry.simulate(
            recipe.ingredients,
            request.context.servings / recipe.servings,
            request.context.target_date,
        )
        recent_count = recent.get(recipe.recipe_public_id, 0)
        score = score_candidate(request, recipe, simulation, recent_count, units)
        scored.append(
            RankedRecipe.model_validate({
                "rank": 1,
                "recipePublicId": recipe.recipe_public_id,
                "totalScore": score.total,
                "scoreComponents": score.components,
                "explanation": explain_ranked_recipe(score, simulation, recent_count),
            })
        )

    if not scored:
        reason = (
            InfeasibleReason.NUTRITION_INFEASIBLE
            if passed_non_nutrition > 0 and nutrition_rejected == passed_non_nutrition
            else InfeasibleReason.NO_ELIGIBLE_RECIPE
        )
        return _infeasible(request, reason)

    scored.sort(key=lambda v: (-v.total_score, str(v.recipe_public_id)))
    top = tuple(
        item.model_copy(update={"rank": rank})
        for rank, item in enumerate(scored[: request.context.result_limit], start=1)
    )
    return RecipeRankingResponse.model_validate({
        "contractVersion": request.contract_version,
        "requestId": request.request_id,
        "algorithmVersion": request.algorithm_version,
        "status": RankingStatus.SUCCEEDED,
        "rankedRecipes": top,
        "infeasibleReason": None,
    })


def _infeasible(
    request: RecipeRankingRequest,
    reason: InfeasibleReason,
) -> RecipeRankingResponse:
    return RecipeRankingResponse.model_validate({
        "contractVersion": request.contract_version,
        "requestId": request.request_id,
        "algorithmVersion": request.algorithm_version,
        "status": RankingStatus.INFEASIBLE,
        "rankedRecipes": (),
        "infeasibleReason": reason,
    })
