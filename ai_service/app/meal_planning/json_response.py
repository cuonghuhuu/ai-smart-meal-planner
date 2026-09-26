"""Serialize validated Decimal amounts as JSON numbers without float coercion."""

from __future__ import annotations

import json
from datetime import date
from decimal import Decimal
from enum import Enum
from uuid import UUID

from app.meal_planning.contracts import MealPlanGenerationResponse


def response_bytes(response: MealPlanGenerationResponse) -> bytes:
    """Pydantic's default Decimal-as-string JSON is not the approved V1 wire type."""

    value = response.model_dump(mode="python", by_alias=True)
    return _encode(value).encode("utf-8")


def _encode(value: object) -> str:
    if isinstance(value, Decimal):
        if not value.is_finite():
            raise ValueError("non-finite Decimal cannot be encoded")
        return format(value, "f")
    if isinstance(value, Enum):
        return _encode(value.value)
    if isinstance(value, UUID):
        return json.dumps(str(value))
    if isinstance(value, date):
        return json.dumps(value.isoformat())
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if value is None or isinstance(value, (bool, int)):
        return json.dumps(value)
    if isinstance(value, (list, tuple)):
        return "[" + ",".join(_encode(item) for item in value) + "]"
    if isinstance(value, dict):
        return "{" + ",".join(
            json.dumps(str(key)) + ":" + _encode(item)
            for key, item in value.items()
        ) + "}"
    raise TypeError(f"unsupported validated response type: {type(value).__name__}")
