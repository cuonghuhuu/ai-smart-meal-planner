"""Stateless orchestration from validated V1 snapshots to V1 AI outcomes."""

from __future__ import annotations

from datetime import timedelta
from decimal import Decimal, ROUND_HALF_UP, localcontext

from app.meal_planning.constraints import unsupported_hard_constraint
from app.meal_planning.contracts import (
    GenerationStatus,
    MealPlanGenerationRequest,
    MealPlanGenerationResponse,
    UnfilledSlotReasonCode,
)
from app.meal_planning.search import SearchConfig, SearchState, append_remaining, search_plan
from app.meal_planning.virtual_pantry import VirtualPantry


def generate_meal_plan(
    request: MealPlanGenerationRequest,
    config: SearchConfig | None = None,
) -> MealPlanGenerationResponse:
    """Generate a deterministic plan; technical failures are not AI outcomes."""

    # Decimal's process/thread default context is mutable. Keep enough precision
    # for V1's validated 14-digit quantities and 18-digit unit factors.
    with localcontext() as context:
        context.prec = 64
        context.rounding = ROUND_HALF_UP
        return _generate_with_fixed_decimal_context(request, config)


def _generate_with_fixed_decimal_context(
    request: MealPlanGenerationRequest,
    config: SearchConfig | None,
) -> MealPlanGenerationResponse:
    resolved = config or SearchConfig()
    schedule = tuple(
        (request.planning.start_date + timedelta(days=day), slot)
        for day in range(request.planning.days)
        for slot in request.planning.requested_meal_slots
    )
    if unsupported_hard_constraint(request):
        pantry = VirtualPantry.from_request(request)
        initial = SearchState((), (), pantry, Decimal("0"), (), None, ())
        result = append_remaining(
            initial, schedule, UnfilledSlotReasonCode.UNSUPPORTED_HARD_CONSTRAINT
        )
    else:
        result = search_plan(request, schedule, resolved)
    status = (
        GenerationStatus.SUCCEEDED if len(result.entries) == len(schedule)
        else GenerationStatus.DEGRADED if result.entries
        else GenerationStatus.INFEASIBLE
    )
    return MealPlanGenerationResponse.model_validate({
        "contractVersion": request.contract_version,
        "requestId": request.request_id,
        "algorithmVersion": request.algorithm_version,
        "status": status,
        "entries": result.entries,
        "unfilledSlots": result.unfilled,
    })
