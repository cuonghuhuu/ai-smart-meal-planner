"""Validation-only FastAPI boundary for Gate P11-B."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.internal_security import require_internal_service
from app.meal_planning.contracts import (
    MealPlanGenerationRequest,
    MealPlanGenerationResponse,
)

router = APIRouter(prefix="/internal/v1", tags=["internal-meal-planning"])


@router.post(
    "/meal-plans/generate",
    response_model=MealPlanGenerationResponse,
    responses={501: {"description": "Planning computation is not implemented in Gate P11-B"}},
    summary="Validate a version-1 meal-planning request",
    description="Gate P11-B validates the contract but does not run a planning algorithm.",
)
def generate_meal_plan(
    request: MealPlanGenerationRequest,
    _: Annotated[None, Depends(require_internal_service)],
) -> JSONResponse:
    """Return an explicit temporary response after strict request validation."""

    return JSONResponse(
        status_code=501,
        content={
            "code": "MEAL_PLANNING_COMPUTATION_NOT_IMPLEMENTED",
            "contractVersion": request.contract_version,
            "requestId": str(request.request_id),
            "algorithmVersion": request.algorithm_version.value,
        },
    )
