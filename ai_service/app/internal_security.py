"""Lightweight service-to-service authentication for internal endpoints."""

from __future__ import annotations

from hmac import compare_digest
from typing import Annotated

from fastapi import Header, HTTPException, Request, status

from app.settings import InternalServiceSettings


def require_internal_service(
    request: Request,
    service_token: Annotated[str | None, Header(alias="X-Internal-Service-Token")] = None,
) -> None:
    """Authenticate Java's service identity without accepting an end-user JWT."""

    settings: InternalServiceSettings = request.app.state.internal_service_settings
    expected = settings.service_token
    if expected is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "INTERNAL_SERVICE_AUTH_NOT_CONFIGURED"},
        )
    if service_token is None or not compare_digest(service_token, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={"code": "INVALID_INTERNAL_SERVICE_CREDENTIAL"},
        )
