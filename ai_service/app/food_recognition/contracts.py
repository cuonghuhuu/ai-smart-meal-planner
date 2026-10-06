"""HTTP contract for ingredient recognition results."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ContractModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class BoundingBox(ContractModel):
    x1: float = Field(ge=0)
    y1: float = Field(ge=0)
    x2: float = Field(ge=0)
    y2: float = Field(ge=0)


class IngredientDetection(ContractModel):
    class_id: int = Field(ge=0)
    code: str = Field(min_length=1, max_length=64)
    name_vi: str = Field(min_length=1, max_length=128)
    confidence: float = Field(ge=0, le=1)
    box: BoundingBox


class IngredientRecognitionResponse(ContractModel):
    algorithm_version: str = "YOLO11N_INGREDIENT_V1"
    image_width: int = Field(gt=0)
    image_height: int = Field(gt=0)
    detections: list[IngredientDetection]
