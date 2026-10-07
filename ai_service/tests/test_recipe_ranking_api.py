import json
from decimal import Decimal
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import create_app
from app.recipe_ranking.contracts import RecipeRankingResponse
from app.settings import InternalServiceSettings

FIXTURE = (
    Path(__file__).resolve().parents[2]
    / "contract_fixtures" / "recipe_recommendation" / "v1" / "valid_request.json"
)
SERVICE_TOKEN = "x" * 32


def test_internal_rank_endpoint_returns_numeric_decimal_json() -> None:
    client = TestClient(create_app(InternalServiceSettings(service_token=SERVICE_TOKEN)))
    response = client.post(
        "/internal/v1/recipe-recommendations/rank",
        content=FIXTURE.read_bytes(),
        headers={
            "Content-Type": "application/json",
            "X-Internal-Service-Token": SERVICE_TOKEN,
        },
    )
    assert response.status_code == 200
    result = RecipeRankingResponse.model_validate_json(response.content)
    assert result.status.value == "SUCCEEDED"
    numeric = json.loads(response.content, parse_float=Decimal)
    assert isinstance(numeric["rankedRecipes"][0]["totalScore"], Decimal)


def test_internal_rank_endpoint_requires_service_credential() -> None:
    client = TestClient(create_app(InternalServiceSettings(service_token=SERVICE_TOKEN)))
    response = client.post(
        "/internal/v1/recipe-recommendations/rank",
        content=FIXTURE.read_bytes(),
        headers={"Content-Type": "application/json"},
    )
    assert response.status_code == 401


def test_invalid_rank_contract_is_rejected_before_computation() -> None:
    client = TestClient(create_app(InternalServiceSettings(service_token=SERVICE_TOKEN)))
    payload = json.loads(FIXTURE.read_text(encoding="utf-8"))
    payload["unexpected"] = True
    response = client.post(
        "/internal/v1/recipe-recommendations/rank",
        json=payload,
        headers={"X-Internal-Service-Token": SERVICE_TOKEN},
    )
    assert response.status_code == 422


def test_rank_request_body_limit_is_enforced() -> None:
    client = TestClient(
        create_app(
            InternalServiceSettings(
                service_token=SERVICE_TOKEN,
                max_request_bytes=1_024,
            )
        )
    )
    response = client.post(
        "/internal/v1/recipe-recommendations/rank",
        content=b"x" * 1_025,
        headers={
            "Content-Type": "application/json",
            "X-Internal-Service-Token": SERVICE_TOKEN,
        },
    )
    assert response.status_code == 413
    assert response.json()["code"] == "AI_REQUEST_TOO_LARGE"
