from __future__ import annotations

import json
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.meal_planning import contract_limits as limits
from app.meal_planning.contracts import (
    AllergenEvidenceStatus,
    GenerationStatus,
    MealPlanGenerationRequest,
    MealPlanGenerationResponse,
    MealSlotCode,
)

FIXTURE_ROOT = (
    Path(__file__).resolve().parents[2] / "contract_fixtures" / "meal_planning" / "v1"
)


def fixture(name: str) -> str:
    return (FIXTURE_ROOT / name).read_text(encoding="utf-8")


def test_valid_request_fixture_is_strictly_typed_and_lot_level() -> None:
    request = MealPlanGenerationRequest.model_validate_json(fixture("valid_request.json"))

    assert request.contract_version == "1"
    assert len(request.pantry_lots) == 2
    assert (
        request.pantry_lots[0].pantry_item_public_id
        != request.pantry_lots[1].pantry_item_public_id
    )
    assert request.ingredient_facts[0].allergen_facts[0].evidence_status.value == "FREE_FROM"


def test_shared_catalog_units_are_canonical_and_exact() -> None:
    request = MealPlanGenerationRequest.model_validate_json(
        fixture("valid_catalog_units_request.json")
    )
    units = {item.unit_code: item for item in request.unit_definitions}
    assert set(units) == {
        "g", "kg", "mg", "mcg", "ml", "l", "tbsp", "floz", "kcal", "kj", "piece",
    }
    assert units["floz"].to_base_factor == Decimal("29.573529562500")
    assert units["kj"].to_base_factor == Decimal("0.239005736138")
    assert request.pantry_lots[0].unit_code == "floz"
    assert request.recipe_candidates[0].ingredients[0].unit_code == "ml"
    assert request.recipe_candidates[0].nutrition.values[0].unit_code == "kj"
    assert request.nutrition_targets[0].unit_code == "kcal"


@pytest.mark.parametrize("field", [
    "target", "definition", "base", "pantry", "ingredient", "nutrition",
])
def test_uppercase_unit_variant_is_rejected_in_every_unit_field(field: str) -> None:
    data = json.loads(fixture("valid_catalog_units_request.json"))
    if field == "target":
        data["nutritionTargets"][0]["unitCode"] = "KCAL"
    elif field == "definition":
        data["unitDefinitions"][0]["unitCode"] = "G"
    elif field == "base":
        data["unitDefinitions"][1]["baseUnitCode"] = "G"
    elif field == "pantry":
        data["pantryLots"][0]["unitCode"] = "ML"
    elif field == "ingredient":
        data["recipeCandidates"][0]["ingredients"][0]["unitCode"] = "ML"
    else:
        data["recipeCandidates"][0]["nutrition"]["values"][0]["unitCode"] = "KCAL"
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(data)


def test_semantic_reference_codes_remain_uppercase() -> None:
    data = json.loads(fixture("valid_request.json"))
    data["nutritionTargets"][0]["nutrientCode"] = "energy"
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(data)


def test_unit_codes_are_not_whitespace_normalized() -> None:
    data = json.loads(fixture("valid_catalog_units_request.json"))
    data["unitDefinitions"][0]["unitCode"] = " g "
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(data)


@pytest.mark.parametrize("factor", [
    Decimal("0"), Decimal("-0.001"), Decimal("0.1234567890123"),
])
def test_invalid_conversion_factor_is_rejected(factor: Decimal) -> None:
    data = json.loads(fixture("valid_catalog_units_request.json"))
    data["unitDefinitions"][5]["toBaseFactor"] = factor
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(data)


def test_catalog_unit_graph_rejects_cross_dimension_and_missing_base() -> None:
    data = json.loads(fixture("valid_catalog_units_request.json"))
    data["unitDefinitions"][5]["baseUnitCode"] = "g"
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(data)
    data["unitDefinitions"][5]["baseUnitCode"] = "ml"
    data["unitDefinitions"] = [item for item in data["unitDefinitions"]
                               if item["unitCode"] != "ml"]
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(data)


def test_valid_boundary_request_accepts_locked_upper_bounds() -> None:
    request = MealPlanGenerationRequest.model_validate_json(
        fixture("valid_boundary_request.json")
    )

    assert request.planning.days == limits.MAX_PLAN_DAYS == 7
    assert len(request.planning.requested_meal_slots) == limits.MAX_REQUESTED_MEAL_SLOTS
    assert limits.MAX_EXPANDED_PLAN_SLOTS == 42


def test_generation_days_accept_one_and_reject_zero() -> None:
    payload = json.loads(fixture("valid_request.json"))
    payload["planning"]["days"] = 1
    assert MealPlanGenerationRequest.model_validate(payload).planning.days == 1

    payload["planning"]["days"] = 0
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate(payload)


@pytest.mark.parametrize(
    "fixture_name",
    [
        "invalid_contract_version_request.json",
        "invalid_decimal_string_request.json",
        "invalid_uuid_request.json",
        "invalid_unknown_field_request.json",
        "invalid_enum_code_request.json",
        "invalid_iso_date_request.json",
        "invalid_plan_days_request.json",
        "invalid_unit_definition_request.json",
        "oversized_allergens_request.json",
    ],
)
def test_invalid_request_fixtures_are_rejected(fixture_name: str) -> None:
    with pytest.raises(ValidationError):
        MealPlanGenerationRequest.model_validate_json(fixture(fixture_name))


@pytest.mark.parametrize(
    ("fixture_name", "expected_status"),
    [
        ("valid_succeeded_response.json", GenerationStatus.SUCCEEDED),
        ("valid_degraded_response.json", GenerationStatus.DEGRADED),
        ("valid_infeasible_response.json", GenerationStatus.INFEASIBLE),
    ],
)
def test_valid_response_fixtures_preserve_algorithm_outcomes(
    fixture_name: str,
    expected_status: GenerationStatus,
) -> None:
    response = MealPlanGenerationResponse.model_validate_json(fixture(fixture_name))

    assert response.status is expected_status


def test_out_of_range_total_score_is_rejected() -> None:
    with pytest.raises(ValidationError):
        MealPlanGenerationResponse.model_validate_json(
            fixture("invalid_score_range_response.json")
        )


@pytest.mark.parametrize(
    ("ingredient_suffix", "expected_evidence"),
    [
        (1, AllergenEvidenceStatus.FREE_FROM),
        (3, AllergenEvidenceStatus.CONTAINS),
        (4, AllergenEvidenceStatus.MAY_CONTAIN),
        (5, AllergenEvidenceStatus.UNKNOWN),
        (6, AllergenEvidenceStatus.UNKNOWN),
        (7, AllergenEvidenceStatus.UNKNOWN),
    ],
)
def test_declared_allergen_evidence_is_fail_closed(
    ingredient_suffix: int,
    expected_evidence: AllergenEvidenceStatus,
) -> None:
    request = MealPlanGenerationRequest.model_validate_json(fixture("valid_request.json"))
    ingredient_id = UUID(f"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa{ingredient_suffix}")

    assert "PEANUT" in request.hard_constraints.allergen_codes
    evidence = request.allergen_evidence_for(ingredient_id, "PEANUT")
    assert evidence is expected_evidence
    assert (evidence is AllergenEvidenceStatus.FREE_FROM) == (ingredient_suffix == 1)


@pytest.mark.parametrize(
    ("fixture_name", "new_status"),
    [
        ("valid_succeeded_response.json", "DEGRADED"),
        ("valid_degraded_response.json", "INFEASIBLE"),
        ("valid_infeasible_response.json", "SUCCEEDED"),
    ],
)
def test_response_status_requires_its_structural_shape(
    fixture_name: str, new_status: str
) -> None:
    payload = json.loads(fixture(fixture_name))
    payload["status"] = new_status

    with pytest.raises(ValidationError):
        MealPlanGenerationResponse.model_validate(payload)


@pytest.mark.parametrize(
    ("change", "value"),
    [
        ("duplicate", "PANTRY_COVERAGE"),
        ("unknown", "UNRECOGNIZED_COMPONENT"),
        ("negative_penalty_weight", -0.20),
        ("wrong_penalty_weight", 0.10),
        ("wrong_coverage_weight", 0.30),
        ("component_below_zero", -0.01),
        ("component_above_one", 1.01),
        ("total_below_minimum", -0.21),
        ("total_above_maximum", 1.01),
    ],
)
def test_score_component_invariants_reject_invalid_values(
    change: str, value: str | float
) -> None:
    payload = json.loads(fixture("valid_succeeded_response.json"))
    entry = payload["entries"][0]
    components = entry["scoreComponents"]
    if change == "duplicate":
        components[1]["componentCode"] = value
    elif change == "unknown":
        components[0]["componentCode"] = value
    elif change == "negative_penalty_weight" or change == "wrong_penalty_weight":
        components[-1]["weight"] = value
    elif change == "wrong_coverage_weight":
        components[0]["weight"] = value
    elif change == "component_below_zero" or change == "component_above_one":
        components[0]["value"] = value
    else:
        entry["totalScore"] = value

    with pytest.raises(ValidationError):
        MealPlanGenerationResponse.model_validate(payload)


def test_missing_score_component_is_rejected() -> None:
    payload = json.loads(fixture("valid_succeeded_response.json"))
    payload["entries"][0]["scoreComponents"].pop()

    with pytest.raises(ValidationError):
        MealPlanGenerationResponse.model_validate(payload)


def test_infeasible_cannot_carry_a_fabricated_recommendation_result() -> None:
    payload = json.loads(fixture("valid_infeasible_response.json"))
    payload["recommendationResults"] = [{"recipePublicId": "fake"}]

    with pytest.raises(ValidationError):
        MealPlanGenerationResponse.model_validate(payload)


def test_combined_response_slots_cannot_exceed_generation_ceiling() -> None:
    payload = json.loads(fixture("valid_succeeded_response.json"))
    payload["status"] = "DEGRADED"
    slots = [
        {
            "planDate": (date(2026, 10, 2) + timedelta(days=day)).isoformat(),
            "mealSlotCode": slot.value,
            "reasonCode": "NO_ELIGIBLE_RECIPE",
            "explanation": None,
        }
        for day in range(limits.MAX_PLAN_DAYS)
        for slot in MealSlotCode
    ]
    payload["unfilledSlots"] = slots[: limits.MAX_EXPANDED_PLAN_SLOTS - 1]
    assert len(payload["entries"]) + len(payload["unfilledSlots"]) == 43

    with pytest.raises(ValidationError):
        MealPlanGenerationResponse.model_validate(payload)


def test_shared_limits_fixture_matches_python_constants() -> None:
    expected = json.loads(fixture("contract_limits.json"))
    for name, value in expected.items():
        assert getattr(limits, name) == value
