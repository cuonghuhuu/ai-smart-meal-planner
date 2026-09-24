"""Environment-backed settings for the internal AI service boundary."""

from __future__ import annotations

import os
from dataclasses import dataclass

from app.meal_planning.contract_limits import MAX_REQUEST_BYTES
from app.meal_planning.search import (
    DEFAULT_BEAM_WIDTH,
    DEFAULT_MAX_EXPANSIONS,
    SearchConfig,
)


@dataclass(frozen=True, slots=True)
class InternalServiceSettings:
    """Small fail-closed configuration surface for internal service calls."""

    service_token: str | None
    max_request_bytes: int = MAX_REQUEST_BYTES
    beam_width: int = DEFAULT_BEAM_WIDTH
    max_search_expansions: int = DEFAULT_MAX_EXPANSIONS

    def __post_init__(self) -> None:
        if self.service_token is not None and len(self.service_token) < 32:
            raise ValueError("AI_INTERNAL_SERVICE_TOKEN must contain at least 32 characters")
        if self.max_request_bytes < 1_024 or self.max_request_bytes > MAX_REQUEST_BYTES:
            raise ValueError(f"max_request_bytes must be between 1024 and {MAX_REQUEST_BYTES}")
        SearchConfig(self.beam_width, self.max_search_expansions)

    @classmethod
    def from_environment(cls) -> InternalServiceSettings:
        raw_limit = os.getenv("AI_MAX_REQUEST_BYTES")
        max_request_bytes = MAX_REQUEST_BYTES if raw_limit is None else int(raw_limit)
        return cls(
            service_token=os.getenv("AI_INTERNAL_SERVICE_TOKEN"),
            max_request_bytes=max_request_bytes,
            beam_width=int(os.getenv("AI_BEAM_WIDTH", str(DEFAULT_BEAM_WIDTH))),
            max_search_expansions=int(
                os.getenv("AI_MAX_SEARCH_EXPANSIONS", str(DEFAULT_MAX_EXPANSIONS))
            ),
        )
