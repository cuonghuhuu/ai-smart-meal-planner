from pathlib import Path

import pytest
from pydantic import ValidationError

from app.recipe_ranking.contracts import (
    AlgorithmVersion,
    RankingStatus,
    RecipeRankingRequest,
    RecipeRankingResponse,
)

ROOT = Path(__file__).resolve().parents[2] / "contract_fixtures" / "recipe_recommendation" / "v1"


def fixture(name: str) -> str:
    return (ROOT / name).read_text(encoding="utf-8")


def test_shared_valid_fixtures_bind_to_v1() -> None:
    request = RecipeRankingRequest.model_validate_json(fixture("valid_request.json"))
    succeeded = RecipeRankingResponse.model_validate_json(
        fixture("valid_succeeded_response.json")
    )
    infeasible = RecipeRankingResponse.model_validate_json(
        fixture("valid_infeasible_response.json")
    )
    assert request.algorithm_version is AlgorithmVersion.HEURISTIC_RECIPE_RANK_V1
    assert succeeded.status is RankingStatus.SUCCEEDED
    assert len(succeeded.ranked_recipes) == 2
    assert infeasible.status is RankingStatus.INFEASIBLE


@pytest.mark.parametrize(
    "name,model",
    [
        ("invalid_algorithm_version_request.json", RecipeRankingRequest),
        ("invalid_unknown_field_request.json", RecipeRankingRequest),
        ("invalid_noncontiguous_rank_response.json", RecipeRankingResponse),
        ("invalid_score_components_response.json", RecipeRankingResponse),
    ],
)
def test_shared_invalid_fixtures_are_rejected(name: str, model: type) -> None:
    with pytest.raises(ValidationError):
        model.model_validate_json(fixture(name))
