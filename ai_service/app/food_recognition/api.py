"""Authenticated internal endpoint for local YOLO ingredient recognition."""

from __future__ import annotations

from functools import lru_cache
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.food_recognition.contracts import IngredientRecognitionResponse
from app.food_recognition.detector import (
    InvalidRecognitionImage,
    RecognitionConfigurationError,
    YoloIngredientDetector,
)
from app.internal_security import require_internal_service

MAX_RECOGNITION_IMAGE_BYTES = 8 * 1024 * 1024

router = APIRouter(prefix="/internal/v1", tags=["internal-food-recognition"])


@lru_cache(maxsize=1)
def get_ingredient_detector() -> YoloIngredientDetector:
    return YoloIngredientDetector.from_environment()


@router.post(
    "/food-recognition/ingredients:detect",
    response_model=IngredientRecognitionResponse,
    summary="Detect Vietnamese ingredient classes in one image",
    description="Local YOLO inference. Nutrition lookup remains Java-owned.",
)
async def detect_ingredients(
    request: Request,
    _: Annotated[None, Depends(require_internal_service)],
    detector: Annotated[YoloIngredientDetector, Depends(get_ingredient_detector)],
) -> IngredientRecognitionResponse:
    image_bytes = await request.body()
    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={"code": "EMPTY_RECOGNITION_IMAGE"},
        )
    if len(image_bytes) > MAX_RECOGNITION_IMAGE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail={
                "code": "RECOGNITION_IMAGE_TOO_LARGE",
                "maxBytes": MAX_RECOGNITION_IMAGE_BYTES,
            },
        )

    try:
        return detector.detect(image_bytes)
    except InvalidRecognitionImage as exc:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail={"code": "INVALID_RECOGNITION_IMAGE", "message": str(exc)},
        ) from exc
    except RecognitionConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "RECOGNITION_NOT_READY", "message": str(exc)},
        ) from exc
