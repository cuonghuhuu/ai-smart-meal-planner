from __future__ import annotations

import asyncio
import json
from decimal import Decimal
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from starlette.types import Message

from app.main import create_app
from app.meal_planning.contract_limits import MAX_REQUEST_BYTES
from app.meal_planning.contracts import MealPlanGenerationResponse
from app.request_size_limit import RequestSizeLimitMiddleware
from app.settings import InternalServiceSettings

FIXTURE_ROOT = (
    Path(__file__).resolve().parents[2] / "contract_fixtures" / "meal_planning" / "v1"
)
SERVICE_TOKEN = "test-internal-service-token-32-characters"


def fixture_bytes(name: str) -> bytes:
    return (FIXTURE_ROOT / name).read_bytes()


def test_validated_request_returns_a_complete_algorithm_outcome() -> None:
    client = TestClient(
        create_app(InternalServiceSettings(service_token=SERVICE_TOKEN))
    )

    response = client.post(
        "/internal/v1/meal-plans/generate",
        content=fixture_bytes("valid_request.json"),
        headers={
            "Content-Type": "application/json",
            "X-Internal-Service-Token": SERVICE_TOKEN,
        },
    )

    assert response.status_code == 200
    result = MealPlanGenerationResponse.model_validate_json(response.content)
    assert result.status.value == "SUCCEEDED"
    assert len(result.entries) == 2
    assert not result.unfilled_slots
    assert result.request_id.hex == "11111111111141118111111111111111"
    numeric_json = json.loads(response.content, parse_float=Decimal)
    assert isinstance(numeric_json["entries"][0]["totalScore"], Decimal)
    assert isinstance(numeric_json["entries"][0]["scoreComponents"][0]["weight"], Decimal)


def test_api_degraded_and_infeasible_are_http_200_outcomes() -> None:
    client = TestClient(create_app(InternalServiceSettings(service_token=SERVICE_TOKEN)))
    payload = json.loads(fixture_bytes("valid_request.json"))
    headers = {"X-Internal-Service-Token": SERVICE_TOKEN}

    payload["recipeCandidates"][0]["mealSlotCodes"] = ["BREAKFAST"]
    degraded = client.post("/internal/v1/meal-plans/generate", json=payload, headers=headers)
    assert degraded.status_code == 200
    assert degraded.json()["status"] == "DEGRADED"
    assert len(degraded.json()["entries"]) == 1
    assert len(degraded.json()["unfilledSlots"]) == 1

    payload["hardConstraints"]["allergenCodes"] = ["TREE_NUT"]
    infeasible = client.post("/internal/v1/meal-plans/generate", json=payload, headers=headers)
    assert infeasible.status_code == 200
    assert infeasible.json()["status"] == "INFEASIBLE"
    assert infeasible.json()["entries"] == []


def test_budget_without_an_entry_is_a_technical_api_error_not_infeasible() -> None:
    data = json.loads(fixture_bytes("valid_request.json"))
    twin = json.loads(json.dumps(data["recipeCandidates"][0]))
    twin["recipePublicId"] = "dddddddd-dddd-4ddd-8ddd-ddddddddddd3"
    data["recipeCandidates"].append(twin)
    client = TestClient(create_app(InternalServiceSettings(
        service_token=SERVICE_TOKEN, max_search_expansions=1,
    )))

    response = client.post(
        "/internal/v1/meal-plans/generate",
        json=data,
        headers={"X-Internal-Service-Token": SERVICE_TOKEN},
    )

    assert response.status_code == 503
    assert response.json() == {
        "code": "AI_SEARCH_BUDGET_EXHAUSTED",
        "requestId": data["requestId"],
        "algorithmVersion": data["algorithmVersion"],
    }
    assert "status" not in response.json()


def test_openapi_exposes_the_versioned_response_contract() -> None:
    application = create_app(InternalServiceSettings(service_token=SERVICE_TOKEN))

    operation = application.openapi()["paths"][
        "/internal/v1/meal-plans/generate"
    ]["post"]

    success_schema = operation["responses"]["200"]["content"][
        "application/json"
    ]["schema"]
    assert success_schema["$ref"].endswith("/MealPlanGenerationResponse")
    assert "501" not in operation["responses"]


@pytest.mark.parametrize(
    "fixture_name",
    ["invalid_unknown_field_request.json", "invalid_plan_days_request.json"],
)
def test_invalid_contract_is_rejected_before_computation(
    fixture_name: str,
) -> None:
    client = TestClient(
        create_app(InternalServiceSettings(service_token=SERVICE_TOKEN))
    )

    response = client.post(
        "/internal/v1/meal-plans/generate",
        content=fixture_bytes(fixture_name),
        headers={
            "Content-Type": "application/json",
            "X-Internal-Service-Token": SERVICE_TOKEN,
        },
    )

    assert response.status_code == 422


def test_internal_endpoint_requires_java_service_credential() -> None:
    client = TestClient(
        create_app(InternalServiceSettings(service_token=SERVICE_TOKEN))
    )

    response = client.post(
        "/internal/v1/meal-plans/generate",
        content=fixture_bytes("valid_boundary_request.json"),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 401
    assert response.json()["detail"]["code"] == "INVALID_INTERNAL_SERVICE_CREDENTIAL"


def test_internal_endpoint_fails_closed_when_auth_is_not_configured() -> None:
    client = TestClient(create_app(InternalServiceSettings(service_token=None)))

    response = client.post(
        "/internal/v1/meal-plans/generate",
        content=fixture_bytes("valid_boundary_request.json"),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "INTERNAL_SERVICE_AUTH_NOT_CONFIGURED"


def test_request_body_limit_is_enforced_before_json_parsing() -> None:
    client = TestClient(
        create_app(
            InternalServiceSettings(
                service_token=SERVICE_TOKEN,
                max_request_bytes=1_024,
            )
        )
    )

    response = client.post(
        "/internal/v1/meal-plans/generate",
        content=b"x" * 1_025,
        headers={
            "Content-Type": "application/json",
            "X-Internal-Service-Token": SERVICE_TOKEN,
        },
    )

    assert response.status_code == 413
    assert response.json()["code"] == "AI_REQUEST_TOO_LARGE"
    assert MAX_REQUEST_BYTES == 5 * 1024 * 1024


def test_request_body_limit_is_enforced_without_content_length() -> None:
    messages = [
        {"type": "http.request", "body": b"x" * 700, "more_body": True},
        {"type": "http.request", "body": b"x" * 400, "more_body": False},
    ]
    sent: list[Message] = []

    async def receive() -> Message:
        return messages.pop(0)

    async def send(message: Message) -> None:
        sent.append(message)

    async def body_reader(scope: dict[str, object], receive, send) -> None:
        del scope
        while (await receive()).get("more_body"):
            pass
        await send({"type": "http.response.start", "status": 204, "headers": []})
        await send({"type": "http.response.body", "body": b""})

    middleware = RequestSizeLimitMiddleware(
        body_reader,
        path="/internal/v1/meal-plans/generate",
        max_bytes=1_024,
    )
    scope = {
        "type": "http",
        "method": "POST",
        "path": "/internal/v1/meal-plans/generate",
        "headers": [],
    }

    asyncio.run(middleware(scope, receive, send))

    assert sent[0]["type"] == "http.response.start"
    assert sent[0]["status"] == 413
