"""FastAPI entry point for the internal AI service."""

from fastapi import FastAPI

from app.meal_planning.api import router as meal_planning_router
from app.request_size_limit import RequestSizeLimitMiddleware
from app.settings import InternalServiceSettings


def health() -> dict[str, str]:
    """Expose process health without accessing application state."""
    return {"status": "ok"}


def create_app(settings: InternalServiceSettings | None = None) -> FastAPI:
    """Build the service with explicit settings for deterministic tests."""

    resolved_settings = settings or InternalServiceSettings.from_environment()
    application = FastAPI(title="AI Smart Meal Planner AI Service", version="0.1.0")
    application.state.internal_service_settings = resolved_settings
    application.add_middleware(
        RequestSizeLimitMiddleware,
        path="/internal/v1/meal-plans/generate",
        max_bytes=resolved_settings.max_request_bytes,
    )
    application.add_api_route("/health", health, methods=["GET"])
    application.include_router(meal_planning_router)
    return application


app = create_app()
