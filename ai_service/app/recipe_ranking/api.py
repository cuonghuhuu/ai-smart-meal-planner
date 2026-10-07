"""Authenticated internal API for Personalized Recipe Ranking V1."""

from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.responses import Response

from app.internal_security import require_internal_service
from app.recipe_ranking.contracts import RecipeRankingRequest, RecipeRankingResponse
from app.recipe_ranking.json_response import response_bytes
from app.recipe_ranking.service import rank_recipes

router = APIRouter(prefix="/internal/v1", tags=["internal-recipe-ranking"])


@router.post(
    "/recipe-recommendations/rank",
    response_model=RecipeRankingResponse,
    summary="Rank personalized Recipe candidates using contract V1",
)
def rank_recipe_recommendations(
    request: RecipeRankingRequest,
    _: Annotated[None, Depends(require_internal_service)],
) -> Response:
    return Response(
        content=response_bytes(rank_recipes(request)),
        media_type="application/json",
    )
