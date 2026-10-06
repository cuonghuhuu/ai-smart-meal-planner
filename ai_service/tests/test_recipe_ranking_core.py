import json
from decimal import Decimal
from pathlib import Path

from app.meal_planning.contracts import ScoreComponentCode
from app.recipe_ranking.contracts import (
    InfeasibleReason,
    RankingStatus,
    RecipeRankingRequest,
)
from app.recipe_ranking.service import rank_recipes

FIXTURE = (
    Path(__file__).resolve().parents[2]
    / "contract_fixtures" / "recipe_recommendation" / "v1" / "valid_request.json"
)


def data() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def request(value: dict | None = None) -> RecipeRankingRequest:
    return RecipeRankingRequest.model_validate(value or data())


def test_shared_request_produces_locked_scores() -> None:
    result = rank_recipes(request())
    assert result.status is RankingStatus.SUCCEEDED
    assert [str(v.recipe_public_id)[-1] for v in result.ranked_recipes] == ["1", "2"]
    assert [v.total_score for v in result.ranked_recipes] == [
        Decimal("0.7417"), Decimal("-0.0361")
    ]


def test_candidate_input_order_does_not_change_ranking() -> None:
    value = data()
    first = rank_recipes(request(value))
    value["recipeCandidates"].reverse()
    assert rank_recipes(request(value)) == first


def test_unsupported_diet_is_infeasible() -> None:
    value = data()
    value["hardConstraints"]["exclusionaryDietaryCodes"] = ["UNSUPPORTED_DIET"]
    result = rank_recipes(request(value))
    assert result.infeasible_reason is InfeasibleReason.UNSUPPORTED_HARD_CONSTRAINT


def test_missing_allergen_evidence_fails_closed() -> None:
    value = data()
    value["ingredientFacts"] = []
    result = rank_recipes(request(value))
    assert result.status is RankingStatus.INFEASIBLE
    assert result.infeasible_reason is InfeasibleReason.NO_ELIGIBLE_RECIPE


def test_hard_daily_maximum_is_not_scaled_to_one_meal() -> None:
    value = data()
    value["nutritionTargets"][0].update(
        targetValue=None, minValue=None, maxValue=100, hardLimit=True
    )
    for candidate in value["recipeCandidates"]:
        candidate["nutrition"] = {
            "completenessRatio": 1,
            "values": [{
                "nutrientCode": "ENERGY",
                "amountPerServing": 60,
                "unitCode": "kcal",
            }],
        }
    result = rank_recipes(request(value))
    assert result.infeasible_reason is InfeasibleReason.NUTRITION_INFEASIBLE


def test_dinner_nutrition_fit_uses_thirty_percent_daily_share() -> None:
    value = data()
    candidate = value["recipeCandidates"][0]
    candidate["nutrition"] = {
        "completenessRatio": 1,
        "values": [{
            "nutrientCode": "ENERGY",
            "amountPerServing": 300,
            "unitCode": "kcal",
        }],
    }
    value["recipeCandidates"] = [candidate]
    value["recentRecipeCounts"] = []
    result = rank_recipes(request(value))
    nutrition = next(
        v.value for v in result.ranked_recipes[0].score_components
        if v.component_code is ScoreComponentCode.NUTRITION_FIT
    )
    assert nutrition == Decimal("1.0000")
