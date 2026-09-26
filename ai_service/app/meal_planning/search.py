"""Deterministic bounded Beam Search over immutable partial meal plans."""

from __future__ import annotations

from bisect import bisect_left
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Mapping
from uuid import UUID

from app.meal_planning import contract_limits as limits
from app.meal_planning.constraints import (
    domain_for_slot,
    hard_maximums_hold,
    hard_minimums_remain_possible,
    nutrition_amounts,
)
from app.meal_planning.contracts import (
    MealPlanEntry,
    MealPlanGenerationRequest,
    MealSlotCode,
    RecipeCandidate,
    UnfilledSlot,
    UnfilledSlotReasonCode,
)
from app.meal_planning.explanation import explain_entry, explain_unfilled
from app.meal_planning.scoring import score_candidate
from app.meal_planning.virtual_pantry import VirtualPantry

DEFAULT_BEAM_WIDTH = 5
DEFAULT_MAX_EXPANSIONS = 50_000
MAX_EXPANSIONS_CEILING = 200_000
SEARCH_BUDGET_ERROR_CODE = "AI_SEARCH_BUDGET_EXHAUSTED"


class SearchBudgetExhausted(RuntimeError):
    """Computation stopped before a safe nonempty partial result was established."""


@dataclass(frozen=True, slots=True)
class SearchConfig:
    beam_width: int = DEFAULT_BEAM_WIDTH
    max_expansions: int = DEFAULT_MAX_EXPANSIONS

    def __post_init__(self) -> None:
        if (
            type(self.beam_width) is not int
            or not 1 <= self.beam_width <= limits.MAX_FUTURE_BEAM_WIDTH
        ):
            raise ValueError("beam_width must be an integer in 1..50")
        if (
            type(self.max_expansions) is not int
            or not 1 <= self.max_expansions <= MAX_EXPANSIONS_CEILING
        ):
            raise ValueError("max_expansions must be an integer in 1..200000")


@dataclass(frozen=True, slots=True)
class SearchState:
    entries: tuple[MealPlanEntry, ...]
    unfilled: tuple[UnfilledSlot, ...]
    pantry: VirtualPantry
    total_score: Decimal
    recipe_history: tuple[UUID, ...]
    current_day: date | None
    daily_nutrition: tuple[tuple[str, Decimal], ...]


def search_plan(
    request: MealPlanGenerationRequest,
    schedule: tuple[tuple[date, MealSlotCode], ...],
    config: SearchConfig,
) -> SearchState:
    """Expand at most K states per slot; never enumerate the full plan space."""

    pantry = VirtualPantry.from_request(request)
    domains_with_reasons = {
        slot: domain_for_slot(request, slot)
        for slot in request.planning.requested_meal_slots
    }
    domains = {slot: pair[0] for slot, pair in domains_with_reasons.items()}
    optimistic = _optimistic_maximums(request, domains, pantry)
    initial = SearchState((), (), pantry, Decimal("0"), (), None, ())
    beam = (initial,)
    day_start_beam = beam
    expansions = 0

    for index, (plan_date, slot) in enumerate(schedule):
        if index == 0 or schedule[index - 1][0] != plan_date:
            day_start_beam = beam
        domain, empty_reason = domains_with_reasons[slot]
        if expansions + len(beam) * len(domain) > config.max_expansions:
            return _on_budget_exhaustion(
                request, beam, day_start_beam, schedule, index
            )
        expanded: list[tuple[tuple[object, ...], SearchState]] = []
        future_slots = tuple(
            next_slot for next_date, next_slot in schedule[index + 1:]
            if next_date == plan_date
        )
        for state in beam:
            daily = dict(state.daily_nutrition) if state.current_day == plan_date else {}
            valid_count = 0
            for recipe in domain:
                expansions += 1
                candidate = _transition(
                    request, state, recipe, plan_date, slot, daily, future_slots, optimistic
                )
                if candidate is not None:
                    _retain_best(expanded, candidate, config.beam_width)
                    valid_count += 1
            if valid_count == 0:
                reason = empty_reason if not domain else UnfilledSlotReasonCode.NUTRITION_INFEASIBLE
                if hard_minimums_remain_possible(
                    request, daily, future_slots, optimistic, state.pantry.units
                ):
                    _retain_best(
                        expanded, _skip(state, plan_date, slot, daily, reason),
                        config.beam_width,
                    )
        if not expanded:
            prefix = min(day_start_beam, key=_rank_key)
            start = index - (index % len(request.planning.requested_meal_slots))
            return append_remaining(
                prefix, schedule[start:], UnfilledSlotReasonCode.NUTRITION_INFEASIBLE
            )
        beam = tuple(state for _, state in expanded)
    return beam[0]


def _on_budget_exhaustion(
    request: MealPlanGenerationRequest,
    beam: tuple[SearchState, ...],
    day_start_beam: tuple[SearchState, ...],
    schedule: tuple[tuple[date, MealSlotCode], ...],
    index: int,
) -> SearchState:
    # A partial current day is safe to return only if its hard daily minima
    # already hold. Otherwise rewind to a completed-day prefix.
    safe = tuple(
        state for state in beam
        if hard_minimums_remain_possible(
            request, dict(state.daily_nutrition), (), {}, state.pantry.units
        )
    )
    if safe:
        prefix = min(safe, key=_rank_key)
        remaining = schedule[index:]
    else:
        prefix = min(day_start_beam, key=_rank_key)
        day_start = index - (index % len(request.planning.requested_meal_slots))
        remaining = schedule[day_start:]
    if not prefix.entries:
        raise SearchBudgetExhausted(SEARCH_BUDGET_ERROR_CODE)
    return append_remaining(
        prefix, remaining, UnfilledSlotReasonCode.SEARCH_LIMIT_REACHED
    )


def _transition(
    request: MealPlanGenerationRequest,
    state: SearchState,
    recipe: RecipeCandidate,
    plan_date: date,
    slot: MealSlotCode,
    daily: Mapping[str, Decimal],
    future_slots: tuple[MealSlotCode, ...],
    optimistic: Mapping[MealSlotCode, Mapping[str, Decimal]],
) -> SearchState | None:
    units = state.pantry.units
    nutrition = nutrition_amounts(recipe, request.planning.default_servings, units)
    updated = dict(daily)
    for code, amount in nutrition.items():
        updated[code] = updated.get(code, Decimal("0")) + amount
    if not hard_maximums_hold(request, updated, units):
        return None
    if not hard_minimums_remain_possible(request, updated, future_slots, optimistic, units):
        return None
    serving_scale = request.planning.default_servings / recipe.servings
    simulation = state.pantry.simulate(recipe.ingredients, serving_scale, plan_date)
    uses = state.recipe_history.count(recipe.recipe_public_id)
    breakdown = score_candidate(request, recipe, simulation, uses, units)
    entry = MealPlanEntry.model_validate({
        "planDate": plan_date,
        "mealSlotCode": slot,
        "recipePublicId": recipe.recipe_public_id,
        "servings": request.planning.default_servings,
        "totalScore": breakdown.total,
        "scoreComponents": breakdown.components,
        "explanation": explain_entry(breakdown, simulation, uses),
    })
    return SearchState(
        entries=state.entries + (entry,),
        unfilled=state.unfilled,
        pantry=simulation.pantry,
        total_score=state.total_score + breakdown.total,
        recipe_history=state.recipe_history + (recipe.recipe_public_id,),
        current_day=plan_date,
        daily_nutrition=tuple(sorted(updated.items())),
    )


def _skip(
    state: SearchState,
    plan_date: date,
    slot: MealSlotCode,
    daily: Mapping[str, Decimal],
    reason: UnfilledSlotReasonCode,
) -> SearchState:
    unfilled = UnfilledSlot.model_validate({
        "planDate": plan_date,
        "mealSlotCode": slot,
        "reasonCode": reason,
        "explanation": explain_unfilled(reason),
    })
    return SearchState(
        state.entries, state.unfilled + (unfilled,), state.pantry, state.total_score,
        state.recipe_history, plan_date, tuple(sorted(daily.items())),
    )


def append_remaining(
    state: SearchState,
    schedule: tuple[tuple[date, MealSlotCode], ...],
    reason: UnfilledSlotReasonCode,
) -> SearchState:
    for plan_date, slot in schedule:
        state = _skip(state, plan_date, slot, {}, reason)
    return state


def _optimistic_maximums(
    request: MealPlanGenerationRequest,
    domains: Mapping[MealSlotCode, tuple[RecipeCandidate, ...]],
    pantry: VirtualPantry,
) -> dict[MealSlotCode, dict[str, Decimal]]:
    result: dict[MealSlotCode, dict[str, Decimal]] = {}
    for slot, candidates in domains.items():
        maximums: dict[str, Decimal] = {}
        for recipe in candidates:
            for code, value in nutrition_amounts(
                recipe, request.planning.default_servings, pantry.units
            ).items():
                maximums[code] = max(maximums.get(code, Decimal("0")), value)
        result[slot] = maximums
    return result


def _rank_key(state: SearchState) -> tuple[object, ...]:
    return (
        -len(state.entries),
        -state.total_score,
        tuple(str(entry.recipe_public_id) for entry in state.entries),
        tuple(slot.reason_code.value for slot in state.unfilled),
    )


def _retain_best(
    best: list[tuple[tuple[object, ...], SearchState]],
    state: SearchState,
    width: int,
) -> None:
    """Keep at most K states while expanding a slot, not K x candidates."""

    key = _rank_key(state)
    if len(best) == width and key >= best[-1][0]:
        return
    position = bisect_left([item[0] for item in best], key)
    best.insert(position, (key, state))
    if len(best) > width:
        best.pop()
