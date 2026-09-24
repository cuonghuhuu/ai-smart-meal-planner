"""Algorithm tests use the shared V1 request as a valid snapshot seed."""

from __future__ import annotations

import json
from datetime import date
from decimal import Decimal, localcontext
from pathlib import Path

import pytest

from app.meal_planning.constraints import candidate_rejection
from app.meal_planning.contracts import (
    GenerationStatus,
    MealPlanGenerationRequest,
    MealSlotCode,
    ScoreComponentCode,
    UnfilledSlotReasonCode,
)
from app.meal_planning.scoring import quantize_score, score_candidate
from app.meal_planning.search import SearchBudgetExhausted, SearchConfig
from app.meal_planning.service import generate_meal_plan
from app.meal_planning.virtual_pantry import VirtualPantry

FIXTURE = (
    Path(__file__).resolve().parents[2]
    / "contract_fixtures" / "meal_planning" / "v1" / "valid_request.json"
)
INGREDIENT_PREFIX = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa"


def payload() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def request(data: dict | None = None) -> MealPlanGenerationRequest:
    return MealPlanGenerationRequest.model_validate(data or payload())


def recipe_score(data: dict | None = None, previous_uses: int = 0):
    snapshot = request(data)
    pantry = VirtualPantry.from_request(snapshot)
    recipe = snapshot.recipe_candidates[0]
    simulation = pantry.simulate(
        recipe.ingredients,
        snapshot.planning.default_servings / recipe.servings,
        snapshot.planning.start_date,
    )
    return score_candidate(snapshot, recipe, simulation, previous_uses, pantry.units)


@pytest.mark.parametrize("suffix", [3, 4, 5, 6, 7])
def test_declared_allergen_is_fail_closed_for_every_non_free_status(suffix: int) -> None:
    data = payload()
    data["hardConstraints"]["avoidIngredientPublicIds"] = []
    data["recipeCandidates"][0]["ingredients"][0]["ingredientPublicId"] = (
        INGREDIENT_PREFIX + str(suffix)
    )
    snapshot = request(data)
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST) is (
        UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    )


def test_explicit_free_from_is_eligible_and_avoid_is_hard_but_dislike_is_soft() -> None:
    data = payload()
    snapshot = request(data)
    recipe = snapshot.recipe_candidates[0]
    assert candidate_rejection(snapshot, recipe, MealSlotCode.BREAKFAST) is None
    assert recipe_score(data).value(ScoreComponentCode.DISLIKE_PENALTY) == Decimal("0.5000")

    data["hardConstraints"]["avoidIngredientPublicIds"] = [INGREDIENT_PREFIX + "1"]
    snapshot = request(data)
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST) is (
        UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    )


def test_optional_ingredient_with_unknown_allergen_evidence_is_not_assumed_omitted() -> None:
    data = payload()
    optional = data["recipeCandidates"][0]["ingredients"][1]
    optional["optional"] = True
    optional["ingredientPublicId"] = INGREDIENT_PREFIX + "6"
    snapshot = request(data)
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST) is (
        UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    )


def test_diet_requires_exact_positive_recipe_evidence_and_unknown_rule_is_unsupported() -> None:
    data = payload()
    data["hardConstraints"]["exclusionaryDietaryCodes"] = ["HALAL"]
    snapshot = request(data)
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST) is (
        UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    )
    data["recipeCandidates"][0]["dietaryCodes"].append("HALAL")
    snapshot = request(data)
    assert candidate_rejection(
        snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST
    ) is None

    data["hardConstraints"]["exclusionaryDietaryCodes"] = ["UNSUPPORTED_DIET"]
    result = generate_meal_plan(request(data))
    assert result.status is GenerationStatus.INFEASIBLE
    assert {slot.reason_code for slot in result.unfilled_slots} == {
        UnfilledSlotReasonCode.UNSUPPORTED_HARD_CONSTRAINT
    }


@pytest.mark.parametrize(
    "diet_codes",
    [
        ["UNSUPPORTED_DIET"],
        ["VEGETARIAN", "UNSUPPORTED_DIET"],
    ],
)
def test_unsupported_hard_diet_invalidates_entire_otherwise_fillable_plan(
    diet_codes: list[str],
) -> None:
    data = payload()
    data["planning"]["days"] = 2
    data["hardConstraints"]["exclusionaryDietaryCodes"] = diet_codes
    assert generate_meal_plan(request()).status is GenerationStatus.SUCCEEDED

    result = generate_meal_plan(request(data))
    assert result.status is GenerationStatus.INFEASIBLE
    assert result.entries == ()
    assert len(result.unfilled_slots) == 4
    assert all(
        slot.reason_code is UnfilledSlotReasonCode.UNSUPPORTED_HARD_CONSTRAINT
        for slot in result.unfilled_slots
    )


def test_unknown_cooking_time_cannot_satisfy_a_hard_limit() -> None:
    snapshot = request()
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[1], MealSlotCode.DINNER) is (
        UnfilledSlotReasonCode.HARD_CONSTRAINT_CONFLICT
    )


def test_mandatory_unknown_quantity_and_missing_hard_nutrition_fail_closed() -> None:
    data = payload()
    data["recipeCandidates"][0]["ingredients"][0].update(quantity=None, unitCode=None)
    snapshot = request(data)
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST) is (
        UnfilledSlotReasonCode.PANTRY_INFEASIBLE
    )
    data = payload()
    data["nutritionTargets"][0]["hardLimit"] = True
    data["recipeCandidates"][0]["nutrition"] = None
    snapshot = request(data)
    assert candidate_rejection(snapshot, snapshot.recipe_candidates[0], MealSlotCode.BREAKFAST) is (
        UnfilledSlotReasonCode.NUTRITION_INFEASIBLE
    )


@pytest.mark.parametrize(
    ("bound", "amount", "missing", "expected"),
    [
        ("maxValue", 50, False, GenerationStatus.SUCCEEDED),
        ("maxValue", 40, False, GenerationStatus.INFEASIBLE),
        ("maxValue", 50, True, GenerationStatus.INFEASIBLE),
        ("minValue", 40, False, GenerationStatus.SUCCEEDED),
        ("minValue", 40, True, GenerationStatus.INFEASIBLE),
    ],
)
def test_hard_nutrition_requires_known_comparable_value_and_enforces_bound(
    bound: str, amount: int, missing: bool, expected: GenerationStatus,
) -> None:
    data = payload()
    data["planning"]["requestedMealSlots"] = ["BREAKFAST"]
    target = data["nutritionTargets"][1]
    target.update(targetValue=amount, minValue=None, maxValue=None, hardLimit=True)
    target[bound] = amount
    data["nutritionTargets"] = [target]
    if missing:
        data["recipeCandidates"][0]["nutrition"]["values"] = [
            data["recipeCandidates"][0]["nutrition"]["values"][0]
        ]
    result = generate_meal_plan(request(data))
    assert result.status is expected
    if missing:
        assert result.entries == ()
        assert result.unfilled_slots[0].reason_code is (
            UnfilledSlotReasonCode.NUTRITION_INFEASIBLE
        )


def test_missing_nonhard_nutrition_remains_eligible_and_scores_neutral() -> None:
    data = payload()
    data["planning"]["requestedMealSlots"] = ["BREAKFAST"]
    data["nutritionTargets"] = [data["nutritionTargets"][1]]
    data["recipeCandidates"][0]["nutrition"]["values"] = [
        data["recipeCandidates"][0]["nutrition"]["values"][0]
    ]
    result = generate_meal_plan(request(data))
    assert result.status is GenerationStatus.SUCCEEDED
    nutrition_fit = next(
        component.value for component in result.entries[0].score_components
        if component.component_code is ScoreComponentCode.NUTRITION_FIT
    )
    assert nutrition_fit == Decimal("0.5000")


def test_immutable_lot_level_consumption_and_no_overdraw() -> None:
    snapshot = request()
    original = VirtualPantry.from_request(snapshot)
    recipe = snapshot.recipe_candidates[0]
    first = original.simulate(recipe.ingredients, Decimal("1"), date(2026, 10, 1))
    second = first.pantry.simulate(recipe.ingredients, Decimal("1"), date(2026, 10, 1))
    third = second.pantry.simulate(recipe.ingredients, Decimal("1"), date(2026, 10, 1))
    assert original.remaining_base == (Decimal("500"), Decimal("200"))
    assert first.pantry.remaining_base == (Decimal("350"), Decimal("100"))
    assert second.pantry.remaining_base == (Decimal("200"), Decimal("0"))
    assert third.coverage == Decimal("0.5")
    assert third.missing_ingredients == 1
    assert all(amount >= 0 for amount in third.pantry.remaining_base)
    assert original.simulate(recipe.ingredients, Decimal("1"), date(2026, 10, 1)) == first


def test_pantry_shortage_reduces_score_without_overdraw_or_claiming_full_stock() -> None:
    data = payload()
    data["planning"]["requestedMealSlots"] = ["BREAKFAST"]
    for lot in data["pantryLots"]:
        lot["quantityRemaining"] = 10
    snapshot = request(data)
    result = generate_meal_plan(snapshot)
    assert result.status is GenerationStatus.SUCCEEDED
    entry = result.entries[0]
    coverage = next(
        component.value for component in entry.score_components
        if component.component_code is ScoreComponentCode.PANTRY_COVERAGE
    )
    assert Decimal("0") < coverage < Decimal("1")
    assert "2 ingredient shortfall(s)" in entry.explanation
    simulation = VirtualPantry.from_request(snapshot).simulate(
        snapshot.recipe_candidates[0].ingredients, Decimal("1"), snapshot.planning.start_date
    )
    assert all(balance >= 0 for balance in simulation.pantry.remaining_base)


def test_earliest_usable_expiry_first_and_expired_lot_excluded() -> None:
    data = payload()
    early = dict(data["pantryLots"][0])
    early["pantryItemPublicId"] = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3"
    early["quantityRemaining"] = 100
    early["expiryDate"] = "2026-10-02"
    data["pantryLots"].append(early)
    snapshot = request(data)
    pantry = VirtualPantry.from_request(snapshot)
    only_first = (snapshot.recipe_candidates[0].ingredients[0],)
    simulation = pantry.simulate(only_first, Decimal("1"), date(2026, 10, 1))
    assert [item.lot_public_id for item in simulation.uses] == [
        snapshot.pantry_lots[2].pantry_item_public_id,
        snapshot.pantry_lots[0].pantry_item_public_id,
    ]
    assert [item.quantity_base for item in simulation.uses] == [Decimal("100"), Decimal("50")]

    data["pantryLots"][2]["expiryDate"] = "2026-09-30"
    snapshot = request(data)
    simulation = VirtualPantry.from_request(snapshot).simulate(
        (snapshot.recipe_candidates[0].ingredients[0],), Decimal("1"), date(2026, 10, 1)
    )
    assert all(item.lot_public_id != snapshot.pantry_lots[2].pantry_item_public_id
               for item in simulation.uses)


def test_same_dimension_conversion_and_no_mass_volume_guess() -> None:
    data = payload()
    data["pantryLots"][0]["quantityRemaining"] = 1
    data["pantryLots"][0]["unitCode"] = "KG"
    snapshot = request(data)
    pantry = VirtualPantry.from_request(snapshot)
    requirement = (snapshot.recipe_candidates[0].ingredients[0],)
    used = pantry.simulate(requirement, Decimal("2"), date(2026, 10, 1))
    assert used.uses[0].quantity_base == Decimal("300")
    assert used.pantry.remaining_base[0] == Decimal("700")

    data["unitDefinitions"].append({
        "unitCode": "ML", "dimension": "VOLUME", "baseUnitCode": "ML", "toBaseFactor": 1,
    })
    data["pantryLots"][0]["unitCode"] = "ML"
    snapshot = request(data)
    pantry = VirtualPantry.from_request(snapshot)
    assert not pantry.units.compatible("ML", "G")
    used = pantry.simulate((snapshot.recipe_candidates[0].ingredients[0],),
                           Decimal("1"), date(2026, 10, 1))
    assert used.coverage == 0
    assert used.uses == ()


def test_all_seven_components_and_exact_decimal_total() -> None:
    score = recipe_score()
    values = {item.component_code: item.value for item in score.components}
    assert len(values) == 7
    assert values == {
        ScoreComponentCode.PANTRY_COVERAGE: Decimal("1.0000"),
        ScoreComponentCode.NUTRITION_FIT: Decimal("0.8867"),
        ScoreComponentCode.EXPIRY_URGENCY: Decimal("0.3571"),
        ScoreComponentCode.PREFERENCE_MATCH: Decimal("1.0000"),
        ScoreComponentCode.VARIETY: Decimal("1.0000"),
        ScoreComponentCode.EFFORT_FIT: Decimal("0.4444"),
        ScoreComponentCode.DISLIKE_PENALTY: Decimal("0.5000"),
    }
    assert score.total == Decimal("0.7796")
    assert score.total.as_tuple().exponent == -4
    assert all(component.value.as_tuple().exponent == -4 for component in score.components)
    assert score.components[-1].weight == Decimal("0.20")


def test_neutral_missing_nutrition_and_preferences_and_variety_quantization() -> None:
    data = payload()
    data["recipeCandidates"][0]["nutrition"] = None
    data["softPreferences"]["preferenceCodes"] = []
    score = recipe_score(data, previous_uses=2)
    assert score.value(ScoreComponentCode.NUTRITION_FIT) == Decimal("0.5000")
    assert score.value(ScoreComponentCode.PREFERENCE_MATCH) == Decimal("0.5000")
    assert score.value(ScoreComponentCode.VARIETY) == Decimal("0.3333")


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("0.12344", "0.1234"),
        ("0.12345", "0.1235"),
        ("0.12346", "0.1235"),
        ("-0.12344", "-0.1234"),
        ("-0.12345", "-0.1235"),
        ("-0.12346", "-0.1235"),
    ],
)
def test_score_quantization_uses_four_place_half_up(raw: str, expected: str) -> None:
    assert quantize_score(Decimal(raw)) == Decimal(expected)


def test_soft_zero_maximum_does_not_score_positive_amount_as_fit() -> None:
    data = payload()
    energy = data["nutritionTargets"][0]
    energy.update(targetValue=None, minValue=None, maxValue=0)
    data["nutritionTargets"] = [energy]
    assert recipe_score(data).value(ScoreComponentCode.NUTRITION_FIT) == Decimal("0.0000")


def test_dislike_is_penalty_only_and_effort_unknown_is_neutral_without_cap() -> None:
    data = payload()
    baseline = recipe_score(data)
    data["softPreferences"]["dislikeIngredientPublicIds"] = [
        INGREDIENT_PREFIX + "1", INGREDIENT_PREFIX + "2",
    ]
    disliked = recipe_score(data)
    assert disliked.value(ScoreComponentCode.DISLIKE_PENALTY) == Decimal("1.0000")
    assert baseline.total - disliked.total == Decimal("0.1000")
    data["planning"]["maxMinutesPerMeal"] = None
    data["recipeCandidates"][0]["totalMinutes"] = None
    assert recipe_score(data).value(ScoreComponentCode.EFFORT_FIT) == Decimal("0.5000")


def test_repeatable_search_is_independent_of_candidate_input_order() -> None:
    data = payload()
    first = generate_meal_plan(request(data))
    assert first.status is GenerationStatus.SUCCEEDED
    assert len(first.entries) == 2
    assert all(len(entry.score_components) == 7 for entry in first.entries)
    data["recipeCandidates"].reverse()
    second = generate_meal_plan(request(data))
    assert first == second


def test_search_uses_a_fixed_decimal_context() -> None:
    snapshot = request()
    expected = generate_meal_plan(snapshot)
    with localcontext() as context:
        context.prec = 8
        assert generate_meal_plan(snapshot) == expected


def test_equal_score_tie_uses_public_recipe_uuid_not_input_order() -> None:
    data = payload()
    data["planning"]["requestedMealSlots"] = ["BREAKFAST"]
    original = data["recipeCandidates"][0]
    twin = json.loads(json.dumps(original))
    twin["recipePublicId"] = "dddddddd-dddd-4ddd-8ddd-ddddddddddd0"
    data["recipeCandidates"] = [original, twin]
    winner = generate_meal_plan(request(data), SearchConfig(beam_width=1))
    assert str(winner.entries[0].recipe_public_id) == twin["recipePublicId"]
    data["recipeCandidates"].reverse()
    assert generate_meal_plan(request(data), SearchConfig(beam_width=1)) == winner


def test_beam_width_two_preserves_branch_that_completes_hard_nutrition_plan() -> None:
    data = payload()
    target = data["nutritionTargets"][1]
    target.update(targetValue=10, minValue=None, maxValue=12, hardLimit=True)
    first = data["recipeCandidates"][0]
    first["mealSlotCodes"] = ["BREAKFAST"]
    first["nutrition"]["values"][1]["amountPerServing"] = 4
    alternative = json.loads(json.dumps(first))
    alternative["recipePublicId"] = "dddddddd-dddd-4ddd-8ddd-ddddddddddd3"
    alternative["tagCodes"] = []
    alternative["nutrition"]["values"][1]["amountPerServing"] = 1.5
    dinner = json.loads(json.dumps(first))
    dinner["recipePublicId"] = "dddddddd-dddd-4ddd-8ddd-ddddddddddd4"
    dinner["mealSlotCodes"] = ["DINNER"]
    data["recipeCandidates"] = [first, alternative, dinner]
    snapshot = request(data)
    narrow = generate_meal_plan(snapshot, SearchConfig(beam_width=1))
    wide = generate_meal_plan(snapshot, SearchConfig(beam_width=2))
    assert narrow.status is GenerationStatus.DEGRADED
    assert wide.status is GenerationStatus.SUCCEEDED
    assert [str(item.recipe_public_id)[-1] for item in wide.entries] == ["3", "4"]


def test_fillable_negative_score_slot_is_not_left_empty_to_preserve_zero_score() -> None:
    data = payload()
    data["planning"]["requestedMealSlots"] = ["BREAKFAST"]
    data["planning"]["maxMinutesPerMeal"] = 25
    data["nutritionTargets"] = [{
        "nutrientCode": "ENERGY", "targetValue": 1, "minValue": None,
        "maxValue": None, "hardLimit": False, "unitCode": "KCAL",
    }]
    data["softPreferences"]["preferenceCodes"] = ["MEDITERRANEAN"]
    data["softPreferences"]["dislikeIngredientPublicIds"] = [
        INGREDIENT_PREFIX + "1", INGREDIENT_PREFIX + "2",
    ]
    for lot in data["pantryLots"]:
        lot["expiryDate"] = "2026-09-30"
        lot["expiryKind"] = "BEST_BEFORE"
    result = generate_meal_plan(request(data))
    assert result.status is GenerationStatus.SUCCEEDED
    assert len(result.entries) == 1 and result.unfilled_slots == ()
    assert result.entries[0].total_score == Decimal("-0.1000")
    assert result.entries[0].total_score < Decimal("0")  # Empty plan's numerical score.


def test_hard_daily_minimum_forward_check_returns_infeasible() -> None:
    data = payload()
    data["nutritionTargets"][1].update(targetValue=200, minValue=200, hardLimit=True)
    result = generate_meal_plan(request(data))
    assert result.status is GenerationStatus.INFEASIBLE
    assert result.entries == ()
    assert all(slot.reason_code is UnfilledSlotReasonCode.NUTRITION_INFEASIBLE
               for slot in result.unfilled_slots)


def test_partial_and_no_safe_recipe_outcomes() -> None:
    data = payload()
    data["recipeCandidates"][0]["mealSlotCodes"] = ["BREAKFAST"]
    partial = generate_meal_plan(request(data))
    assert partial.status is GenerationStatus.DEGRADED
    assert len(partial.entries) == 1 and len(partial.unfilled_slots) == 1

    data["hardConstraints"]["allergenCodes"] = ["TREE_NUT"]
    infeasible = generate_meal_plan(request(data))
    assert infeasible.status is GenerationStatus.INFEASIBLE
    assert not infeasible.entries and len(infeasible.unfilled_slots) == 2


def test_expansion_budget_is_bounded_and_preserves_completed_day() -> None:
    data = payload()
    data["planning"]["days"] = 2
    result = generate_meal_plan(request(data), SearchConfig(max_expansions=2))
    assert result.status is GenerationStatus.DEGRADED
    assert len(result.entries) == 2
    assert len(result.unfilled_slots) == 2
    assert all(slot.reason_code is UnfilledSlotReasonCode.SEARCH_LIMIT_REACHED
               for slot in result.unfilled_slots)


def test_budget_after_first_valid_entry_returns_degraded_partial_day() -> None:
    result = generate_meal_plan(request(), SearchConfig(max_expansions=1))
    assert result.status is GenerationStatus.DEGRADED
    assert len(result.entries) == 1
    assert len(result.unfilled_slots) == 1
    assert result.unfilled_slots[0].reason_code is UnfilledSlotReasonCode.SEARCH_LIMIT_REACHED


def test_budget_before_any_entry_raises_typed_technical_exception() -> None:
    data = payload()
    twin = json.loads(json.dumps(data["recipeCandidates"][0]))
    twin["recipePublicId"] = "dddddddd-dddd-4ddd-8ddd-ddddddddddd3"
    data["recipeCandidates"].append(twin)
    with pytest.raises(SearchBudgetExhausted, match="AI_SEARCH_BUDGET_EXHAUSTED"):
        generate_meal_plan(request(data), SearchConfig(max_expansions=1))


@pytest.mark.parametrize("width", [0, 51, True])
def test_beam_width_outside_locked_bound_is_rejected(width: int) -> None:
    with pytest.raises(ValueError):
        SearchConfig(beam_width=width)
