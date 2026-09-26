"""Strict Pydantic transport models for meal-planning contract version 1.

This module contains no planning or scoring behavior. Missing allergen facts are
semantically UNKNOWN; only explicit FREE_FROM evidence is safe for a declared
allergen.
"""

from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from enum import Enum
from typing import Annotated, Self
from uuid import UUID

from pydantic import (
    BaseModel,
    BeforeValidator,
    ConfigDict,
    Field,
    StrictBool,
    StringConstraints,
    model_validator,
)

from app.meal_planning import contract_limits as limits


def _to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ContractModel(BaseModel):
    """Base configuration shared by every version-1 contract model."""

    model_config = ConfigDict(
        alias_generator=_to_camel,
        extra="forbid",
        frozen=True,
        populate_by_name=False,
        str_strip_whitespace=True,
        validate_default=True,
    )


ReferenceCode = Annotated[
    str,
    StringConstraints(
        min_length=1,
        max_length=limits.MAX_REFERENCE_CODE_LENGTH,
        pattern=limits.REFERENCE_CODE_PATTERN,
    ),
]

UnitCode = Annotated[
    str,
    StringConstraints(
        strip_whitespace=False,
        min_length=1,
        max_length=limits.MAX_REFERENCE_CODE_LENGTH,
        pattern=limits.UNIT_CODE_PATTERN,
    ),
]


def _require_json_decimal(value: object) -> object:
    if isinstance(value, (str, bool)):
        raise ValueError("decimal fields must be JSON numbers")
    return value


DecimalNumber = Annotated[Decimal, BeforeValidator(_require_json_decimal)]
NonNegativeAmount = Annotated[
    DecimalNumber,
    Field(ge=Decimal("0"), max_digits=14, decimal_places=4, allow_inf_nan=False),
]
PositiveQuantity = Annotated[
    DecimalNumber,
    Field(gt=Decimal("0"), max_digits=14, decimal_places=4, allow_inf_nan=False),
]
Servings = Annotated[
    DecimalNumber,
    Field(
        gt=Decimal("0"),
        le=Decimal("50.00"),
        max_digits=4,
        decimal_places=2,
        allow_inf_nan=False,
    ),
]
NormalizedValue = Annotated[
    DecimalNumber,
    Field(
        ge=Decimal("0"),
        le=Decimal("1"),
        max_digits=7,
        decimal_places=6,
        allow_inf_nan=False,
    ),
]
TotalScore = Annotated[
    DecimalNumber,
    Field(
        ge=Decimal("-0.20"),
        le=Decimal("1.00"),
        max_digits=7,
        decimal_places=6,
        allow_inf_nan=False,
    ),
]
PositiveWeight = Annotated[
    DecimalNumber,
    Field(
        gt=Decimal("0"),
        le=Decimal("1"),
        max_digits=3,
        decimal_places=2,
        allow_inf_nan=False,
    ),
]


def _parse_iso_date(value: object) -> date:
    if isinstance(value, datetime):
        raise ValueError("date must use YYYY-MM-DD without a time")
    if isinstance(value, date):
        return value
    if not isinstance(value, str) or len(value) != 10:
        raise ValueError("date must use YYYY-MM-DD")
    if value[4] != "-" or value[7] != "-":
        raise ValueError("date must use YYYY-MM-DD")
    try:
        parsed = date.fromisoformat(value)
    except ValueError as exception:
        raise ValueError("date must use YYYY-MM-DD") from exception
    if parsed.isoformat() != value:
        raise ValueError("date must use YYYY-MM-DD")
    return parsed


IsoDate = Annotated[date, BeforeValidator(_parse_iso_date)]


class AlgorithmVersion(str, Enum):
    HEURISTIC_MEAL_PLAN_V1 = "HEURISTIC_MEAL_PLAN_V1"


class MealSlotCode(str, Enum):
    BREAKFAST = "BREAKFAST"
    MORNING_SNACK = "MORNING_SNACK"
    LUNCH = "LUNCH"
    AFTERNOON_SNACK = "AFTERNOON_SNACK"
    DINNER = "DINNER"
    EVENING_SNACK = "EVENING_SNACK"


class UnitDimension(str, Enum):
    MASS = "MASS"
    VOLUME = "VOLUME"
    COUNT = "COUNT"
    ENERGY = "ENERGY"


class ExpiryKind(str, Enum):
    USE_BY = "USE_BY"
    BEST_BEFORE = "BEST_BEFORE"
    UNKNOWN = "UNKNOWN"


class StorageLocation(str, Enum):
    PANTRY = "PANTRY"
    FRIDGE = "FRIDGE"
    FREEZER = "FREEZER"
    OTHER = "OTHER"


class AllergenEvidenceStatus(str, Enum):
    CONTAINS = "CONTAINS"
    MAY_CONTAIN = "MAY_CONTAIN"
    FREE_FROM = "FREE_FROM"
    UNKNOWN = "UNKNOWN"


class GenerationStatus(str, Enum):
    SUCCEEDED = "SUCCEEDED"
    DEGRADED = "DEGRADED"
    INFEASIBLE = "INFEASIBLE"


class ScoreComponentCode(str, Enum):
    PANTRY_COVERAGE = "PANTRY_COVERAGE"
    NUTRITION_FIT = "NUTRITION_FIT"
    EXPIRY_URGENCY = "EXPIRY_URGENCY"
    PREFERENCE_MATCH = "PREFERENCE_MATCH"
    VARIETY = "VARIETY"
    EFFORT_FIT = "EFFORT_FIT"
    DISLIKE_PENALTY = "DISLIKE_PENALTY"


EXPECTED_COMPONENT_WEIGHTS: dict[ScoreComponentCode, Decimal] = {
    ScoreComponentCode.PANTRY_COVERAGE: Decimal("0.40"),
    ScoreComponentCode.NUTRITION_FIT: Decimal("0.25"),
    ScoreComponentCode.EXPIRY_URGENCY: Decimal("0.10"),
    ScoreComponentCode.PREFERENCE_MATCH: Decimal("0.10"),
    ScoreComponentCode.VARIETY: Decimal("0.10"),
    ScoreComponentCode.EFFORT_FIT: Decimal("0.05"),
    ScoreComponentCode.DISLIKE_PENALTY: Decimal("0.20"),
}


class UnfilledSlotReasonCode(str, Enum):
    NO_ELIGIBLE_RECIPE = "NO_ELIGIBLE_RECIPE"
    HARD_CONSTRAINT_CONFLICT = "HARD_CONSTRAINT_CONFLICT"
    UNSUPPORTED_HARD_CONSTRAINT = "UNSUPPORTED_HARD_CONSTRAINT"
    PANTRY_INFEASIBLE = "PANTRY_INFEASIBLE"
    NUTRITION_INFEASIBLE = "NUTRITION_INFEASIBLE"
    SEARCH_LIMIT_REACHED = "SEARCH_LIMIT_REACHED"


class Planning(ContractModel):
    start_date: IsoDate
    days: Annotated[int, Field(strict=True, ge=1, le=limits.MAX_PLAN_DAYS)]
    requested_meal_slots: Annotated[
        tuple[MealSlotCode, ...],
        Field(min_length=1, max_length=limits.MAX_REQUESTED_MEAL_SLOTS),
    ]
    default_servings: Servings
    max_minutes_per_meal: Annotated[
        int | None,
        Field(strict=True, ge=1, le=limits.MAX_MINUTES_PER_MEAL),
    ]

    @model_validator(mode="after")
    def meal_slots_are_unique(self) -> Self:
        if len(set(self.requested_meal_slots)) != len(self.requested_meal_slots):
            raise ValueError("requestedMealSlots must not contain duplicates")
        return self


class HardConstraints(ContractModel):
    allergen_codes: Annotated[
        tuple[ReferenceCode, ...], Field(max_length=limits.MAX_ALLERGEN_CODES)
    ]
    exclusionary_dietary_codes: Annotated[
        tuple[ReferenceCode, ...], Field(max_length=limits.MAX_DIETARY_CODES)
    ]
    avoid_ingredient_public_ids: Annotated[
        tuple[UUID, ...], Field(max_length=limits.MAX_INGREDIENT_PREFERENCE_IDS)
    ]

    @model_validator(mode="after")
    def arrays_are_unique(self) -> Self:
        _require_unique(self.allergen_codes, "allergenCodes")
        _require_unique(self.exclusionary_dietary_codes, "exclusionaryDietaryCodes")
        _require_unique(self.avoid_ingredient_public_ids, "avoidIngredientPublicIds")
        return self


class SoftPreferences(ContractModel):
    dislike_ingredient_public_ids: Annotated[
        tuple[UUID, ...], Field(max_length=limits.MAX_INGREDIENT_PREFERENCE_IDS)
    ]
    preference_codes: Annotated[
        tuple[ReferenceCode, ...], Field(max_length=limits.MAX_PREFERENCE_CODES)
    ]

    @model_validator(mode="after")
    def arrays_are_unique(self) -> Self:
        _require_unique(self.dislike_ingredient_public_ids, "dislikeIngredientPublicIds")
        _require_unique(self.preference_codes, "preferenceCodes")
        return self


class NutritionTarget(ContractModel):
    nutrient_code: ReferenceCode
    target_value: NonNegativeAmount | None
    min_value: NonNegativeAmount | None
    max_value: NonNegativeAmount | None
    hard_limit: StrictBool
    unit_code: UnitCode

    @model_validator(mode="after")
    def values_are_present_and_ordered(self) -> Self:
        if self.target_value is None and self.min_value is None and self.max_value is None:
            raise ValueError("targetValue, minValue or maxValue is required")
        if self.min_value is not None and self.max_value is not None:
            if self.min_value > self.max_value:
                raise ValueError("minValue must not exceed maxValue")
        if self.target_value is not None and self.min_value is not None:
            if self.target_value < self.min_value:
                raise ValueError("targetValue must not be below minValue")
        if self.target_value is not None and self.max_value is not None:
            if self.target_value > self.max_value:
                raise ValueError("targetValue must not exceed maxValue")
        return self


class UnitDefinition(ContractModel):
    unit_code: UnitCode
    dimension: UnitDimension
    base_unit_code: UnitCode
    to_base_factor: Annotated[
        DecimalNumber,
        Field(gt=Decimal("0"), max_digits=22, decimal_places=12, allow_inf_nan=False),
    ]


class PantryLot(ContractModel):
    pantry_item_public_id: UUID
    ingredient_public_id: UUID
    food_public_id: UUID | None
    quantity_remaining: PositiveQuantity
    unit_code: UnitCode
    expiry_date: IsoDate | None
    expiry_kind: ExpiryKind
    storage_location: StorageLocation

    @model_validator(mode="after")
    def expiry_semantics_are_valid(self) -> Self:
        if self.expiry_date is None and self.expiry_kind is not ExpiryKind.UNKNOWN:
            raise ValueError("a missing expiryDate requires expiryKind UNKNOWN")
        return self


class AllergenFact(ContractModel):
    allergen_code: ReferenceCode
    evidence_status: AllergenEvidenceStatus


class IngredientFact(ContractModel):
    ingredient_public_id: UUID
    allergen_facts: Annotated[
        tuple[AllergenFact, ...],
        Field(max_length=limits.MAX_ALLERGEN_FACTS_PER_INGREDIENT),
    ]

    @model_validator(mode="after")
    def allergen_codes_are_unique(self) -> Self:
        _require_unique(
            tuple(fact.allergen_code for fact in self.allergen_facts), "allergenFacts"
        )
        return self


class IngredientRequirement(ContractModel):
    ingredient_public_id: UUID
    quantity: PositiveQuantity | None
    unit_code: UnitCode | None
    optional: StrictBool
    allow_substitution: StrictBool

    @model_validator(mode="after")
    def quantity_and_unit_are_paired(self) -> Self:
        if (self.quantity is None) != (self.unit_code is None):
            raise ValueError("quantity and unitCode must both be present or both be null")
        return self


class NutritionValue(ContractModel):
    nutrient_code: ReferenceCode
    amount_per_serving: NonNegativeAmount
    unit_code: UnitCode


class NutritionData(ContractModel):
    completeness_ratio: NormalizedValue
    values: Annotated[
        tuple[NutritionValue, ...],
        Field(max_length=limits.MAX_NUTRITION_VALUES_PER_RECIPE),
    ]

    @model_validator(mode="after")
    def nutrient_codes_are_unique(self) -> Self:
        _require_unique(tuple(value.nutrient_code for value in self.values), "values")
        return self


class RecipeCandidate(ContractModel):
    recipe_public_id: UUID
    servings: Servings
    total_minutes: Annotated[
        int | None,
        Field(strict=True, ge=0, le=limits.MAX_MINUTES_PER_MEAL),
    ]
    meal_slot_codes: Annotated[
        tuple[MealSlotCode, ...],
        Field(min_length=1, max_length=limits.MAX_REQUESTED_MEAL_SLOTS),
    ]
    dietary_codes: Annotated[
        tuple[ReferenceCode, ...], Field(max_length=limits.MAX_DIETARY_CODES)
    ]
    tag_codes: Annotated[
        tuple[ReferenceCode, ...], Field(max_length=limits.MAX_RECIPE_TAG_CODES)
    ]
    ingredients: Annotated[
        tuple[IngredientRequirement, ...],
        Field(min_length=1, max_length=limits.MAX_INGREDIENTS_PER_RECIPE),
    ]
    nutrition: NutritionData | None

    @model_validator(mode="after")
    def recipe_arrays_are_unique(self) -> Self:
        _require_unique(self.meal_slot_codes, "mealSlotCodes")
        _require_unique(self.dietary_codes, "dietaryCodes")
        _require_unique(self.tag_codes, "tagCodes")
        _require_unique(
            tuple(item.ingredient_public_id for item in self.ingredients), "ingredients"
        )
        return self


class MealPlanGenerationRequest(ContractModel):
    contract_version: Annotated[str, Field(pattern=r"^1$")]
    request_id: UUID
    algorithm_version: AlgorithmVersion
    planning: Planning
    hard_constraints: HardConstraints
    soft_preferences: SoftPreferences
    nutrition_targets: Annotated[
        tuple[NutritionTarget, ...], Field(max_length=limits.MAX_NUTRITION_TARGETS)
    ]
    unit_definitions: Annotated[
        tuple[UnitDefinition, ...], Field(max_length=limits.MAX_UNIT_DEFINITIONS)
    ]
    pantry_lots: Annotated[
        tuple[PantryLot, ...], Field(max_length=limits.MAX_PANTRY_LOTS)
    ]
    ingredient_facts: Annotated[
        tuple[IngredientFact, ...], Field(max_length=limits.MAX_INGREDIENT_FACTS)
    ]
    recipe_candidates: Annotated[
        tuple[RecipeCandidate, ...], Field(max_length=limits.MAX_RECIPE_CANDIDATES)
    ]

    def allergen_evidence_for(
        self, ingredient_public_id: UUID, allergen_code: str
    ) -> AllergenEvidenceStatus:
        """Resolve absent ingredient or allergen facts to UNKNOWN."""

        for ingredient in self.ingredient_facts:
            if ingredient.ingredient_public_id == ingredient_public_id:
                for fact in ingredient.allergen_facts:
                    if fact.allergen_code == allergen_code:
                        return fact.evidence_status
                break
        return AllergenEvidenceStatus.UNKNOWN

    @model_validator(mode="after")
    def root_identities_are_unique(self) -> Self:
        _require_unique(
            tuple(item.nutrient_code for item in self.nutrition_targets), "nutritionTargets"
        )
        _require_unique(
            tuple(item.unit_code for item in self.unit_definitions), "unitDefinitions"
        )
        _require_unique(
            tuple(item.pantry_item_public_id for item in self.pantry_lots), "pantryLots"
        )
        _require_unique(
            tuple(item.ingredient_public_id for item in self.ingredient_facts),
            "ingredientFacts",
        )
        _require_unique(
            tuple(item.recipe_public_id for item in self.recipe_candidates), "recipeCandidates"
        )
        return self

    @model_validator(mode="after")
    def unit_definition_graph_is_safe_and_complete(self) -> Self:
        definitions = {item.unit_code: item for item in self.unit_definitions}
        for definition in self.unit_definitions:
            base = definitions.get(definition.base_unit_code)
            if base is None:
                raise ValueError("every baseUnitCode must have a unit definition")
            if base.dimension is not definition.dimension:
                raise ValueError("unit and base unit must use the same dimension")
            if base.base_unit_code != base.unit_code or base.to_base_factor != Decimal("1"):
                raise ValueError("each base unit must be self-referential with factor 1")

        referenced = {item.unit_code for item in self.nutrition_targets}
        referenced.update(item.unit_code for item in self.pantry_lots)
        for recipe in self.recipe_candidates:
            referenced.update(
                item.unit_code for item in recipe.ingredients if item.unit_code is not None
            )
            if recipe.nutrition is not None:
                referenced.update(item.unit_code for item in recipe.nutrition.values)
        missing = referenced.difference(definitions)
        if missing:
            raise ValueError("every referenced unitCode must have a unit definition")
        return self


class ScoreComponent(ContractModel):
    component_code: ScoreComponentCode
    value: NormalizedValue
    weight: PositiveWeight

    @model_validator(mode="after")
    def weight_matches_algorithm_version(self) -> Self:
        expected = EXPECTED_COMPONENT_WEIGHTS[self.component_code]
        if self.weight != expected:
            raise ValueError(f"weight must be {expected} for {self.component_code.value}")
        return self


class MealPlanEntry(ContractModel):
    plan_date: IsoDate
    meal_slot_code: MealSlotCode
    recipe_public_id: UUID
    servings: Servings
    total_score: TotalScore
    score_components: Annotated[
        tuple[ScoreComponent, ...],
        Field(min_length=limits.MAX_SCORE_COMPONENTS, max_length=limits.MAX_SCORE_COMPONENTS),
    ]
    explanation: Annotated[
        str,
        StringConstraints(min_length=1, max_length=limits.MAX_EXPLANATION_LENGTH),
    ]

    @model_validator(mode="after")
    def score_component_set_is_complete(self) -> Self:
        codes = tuple(component.component_code for component in self.score_components)
        if len(set(codes)) != len(codes) or set(codes) != set(ScoreComponentCode):
            raise ValueError("all seven score component codes are required exactly once")
        return self


class UnfilledSlot(ContractModel):
    plan_date: IsoDate
    meal_slot_code: MealSlotCode
    reason_code: UnfilledSlotReasonCode
    explanation: Annotated[
        str, StringConstraints(max_length=limits.MAX_EXPLANATION_LENGTH)
    ] | None


class MealPlanGenerationResponse(ContractModel):
    contract_version: Annotated[str, Field(pattern=r"^1$")]
    request_id: UUID
    algorithm_version: AlgorithmVersion
    status: GenerationStatus
    entries: Annotated[
        tuple[MealPlanEntry, ...], Field(max_length=limits.MAX_EXPANDED_PLAN_SLOTS)
    ]
    unfilled_slots: Annotated[
        tuple[UnfilledSlot, ...], Field(max_length=limits.MAX_EXPANDED_PLAN_SLOTS)
    ]

    @model_validator(mode="after")
    def status_and_slots_are_consistent(self) -> Self:
        if len(self.entries) + len(self.unfilled_slots) > limits.MAX_EXPANDED_PLAN_SLOTS:
            raise ValueError("combined entries and unfilledSlots exceed the V1 slot limit")
        if self.status is GenerationStatus.SUCCEEDED:
            if not self.entries or self.unfilled_slots:
                raise ValueError("SUCCEEDED requires entries and no unfilledSlots")
        elif self.status is GenerationStatus.DEGRADED:
            if not self.entries or not self.unfilled_slots:
                raise ValueError("DEGRADED requires entries and unfilledSlots")
        elif self.entries or not self.unfilled_slots:
            raise ValueError("INFEASIBLE requires no entries and at least one unfilledSlot")

        filled = tuple((entry.plan_date, entry.meal_slot_code) for entry in self.entries)
        unfilled = tuple((slot.plan_date, slot.meal_slot_code) for slot in self.unfilled_slots)
        _require_unique(filled, "entries")
        _require_unique(unfilled, "unfilledSlots")
        if set(filled).intersection(unfilled):
            raise ValueError("a date/slot pair cannot be both filled and unfilled")
        return self


def _require_unique(values: tuple[object, ...], field_name: str) -> None:
    if len(set(values)) != len(values):
        raise ValueError(f"{field_name} must not contain duplicates")
