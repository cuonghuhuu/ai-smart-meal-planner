"""Authenticated version-1 AI meal-planning boundary."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse, Response

from app.internal_security import require_internal_service
from app.meal_planning.contracts import (
    MealPlanGenerationRequest,
    MealPlanGenerationResponse,
)
from app.meal_planning.json_response import response_bytes
from app.meal_planning.search import (
    SEARCH_BUDGET_ERROR_CODE,
    SearchBudgetExhausted,
    SearchConfig,
)
from app.meal_planning.service import generate_meal_plan as compute_plan

router = APIRouter(prefix="/internal/v1", tags=["internal-meal-planning"])


@router.post(
    "/meal-plans/generate",
    response_model=MealPlanGenerationResponse,
    responses={503: {"description": "AI search budget exhausted before a safe result"}},
    summary="Generate a version-1 AI meal plan",
    description="Deterministic bounded search on a validated, immutable Java snapshot.",
)
def generate_meal_plan(
    request: MealPlanGenerationRequest,
    http_request: Request,
    _: Annotated[None, Depends(require_internal_service)],
) -> Response:
    """Return a validated AI outcome, never a Java technical FAILED status."""

    settings = http_request.app.state.internal_service_settings
    try:
        result = compute_plan(
            request, SearchConfig(settings.beam_width, settings.max_search_expansions)
        )
    except SearchBudgetExhausted:
        return JSONResponse(
            status_code=503,
            content={
                "code": SEARCH_BUDGET_ERROR_CODE,
                "requestId": str(request.request_id),
                "algorithmVersion": request.algorithm_version.value,
            },
        )
    return Response(content=response_bytes(result), media_type="application/json")
