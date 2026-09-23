from __future__ import annotations

import asyncio
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from starlette.types import Message

from app.main import create_app
from app.meal_planning.contract_limits import MAX_REQUEST_BYTES
from app.request_size_limit import RequestSizeLimitMiddleware
from app.settings import InternalServiceSettings

FIXTURE_ROOT = (
    Path(__file__).resolve().parents[2] / "contract_fixtures" / "meal_planning" / "v1"
)
SERVICE_TOKEN = "test-internal-service-token-32-characters"


def fixture_bytes(name: str) -> bytes:
    return (FIXTURE_ROOT / name).read_bytes()


def test_validated_request_reaches_temporary_not_implemented_boundary() -> None:
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

    assert response.status_code == 501
    assert response.json() == {
        "code": "MEAL_PLANNING_COMPUTATION_NOT_IMPLEMENTED",
        "contractVersion": "1",
        "requestId": "11111111-1111-4111-8111-111111111111",
        "algorithmVersion": "HEURISTIC_MEAL_PLAN_V1",
    }


def test_openapi_exposes_the_versioned_response_contract() -> None:
    application = create_app(InternalServiceSettings(service_token=SERVICE_TOKEN))

    operation = application.openapi()["paths"][
        "/internal/v1/meal-plans/generate"
    ]["post"]

    success_schema = operation["responses"]["200"]["content"][
        "application/json"
    ]["schema"]
    assert success_schema["$ref"].endswith("/MealPlanGenerationResponse")
    assert "501" in operation["responses"]


@pytest.mark.parametrize(
    "fixture_name",
    ["invalid_unknown_field_request.json", "invalid_plan_days_request.json"],
)
def test_invalid_contract_is_rejected_before_temporary_response(
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
