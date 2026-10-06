"""Serialize validated ranking Decimal values as JSON numbers."""

import json
from datetime import date
from decimal import Decimal
from enum import Enum
from uuid import UUID

from app.recipe_ranking.contracts import RecipeRankingResponse


def response_bytes(response: RecipeRankingResponse) -> bytes:
    return _encode(response.model_dump(mode="python", by_alias=True)).encode("utf-8")


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
        return "[" + ",".join(_encode(v) for v in value) + "]"
    if isinstance(value, dict):
        return "{" + ",".join(
            json.dumps(str(k)) + ":" + _encode(v) for k, v in value.items()
        ) + "}"
    raise TypeError(f"unsupported validated response type: {type(value).__name__}")
