"""Strict Pydantic models for Personalized Recipe Ranking contract V1."""

from __future__ import annotations

from decimal import Decimal, ROUND_HALF_UP
from enum import Enum
from typing import Annotated, Self
from uuid import UUID

from pydantic import Field, StringConstraints, model_validator

from app.meal_planning.contracts import (
    ContractModel,
    DecimalNumber,
    EXPECTED_COMPONENT_WEIGHTS,
    HardConstraints,
    IngredientFact,
    IsoDate,
    MealSlotCode,
    NutritionTarget,
    PantryLot,
    PositiveWeight,
    RecipeCandidate,
    ScoreComponentCode,
    SoftPreferences,
    UnitDefinition,
)
from app.recipe_ranking import contract_limits as limits


class AlgorithmVersion(str, Enum):
    HEURISTIC_RECIPE_RANK_V1 = "HEURISTIC_RECIPE_RANK_V1"


class RankingStatus(str, Enum):
    SUCCEEDED = "SUCCEEDED"
    INFEASIBLE = "INFEASIBLE"


class InfeasibleReason(str, Enum):
    NO_ELIGIBLE_RECIPE = "NO_ELIGIBLE_RECIPE"
    UNSUPPORTED_HARD_CONSTRAINT = "UNSUPPORTED_HARD_CONSTRAINT"
    NUTRITION_INFEASIBLE = "NUTRITION_INFEASIBLE"


RankingValue = Annotated[
    DecimalNumber,
    Field(ge=Decimal("0"), le=Decimal("1"), max_digits=7, decimal_places=4,
          allow_inf_nan=False),
]
RankingTotal = Annotated[
    DecimalNumber,
    Field(ge=Decimal("-0.20"), le=Decimal("1.00"), max_digits=7,
          decimal_places=4, allow_inf_nan=False),
]


class Context(ContractModel):
    target_date: IsoDate
    meal_slot_code: MealSlotCode
    servings: Annotated[
        DecimalNumber,
        Field(gt=Decimal("0"), le=Decimal("50.00"), max_digits=4,
              decimal_places=2, allow_inf_nan=False),
    ]
    max_minutes: Annotated[int | None, Field(strict=True, ge=1, le=limits.MAX_MINUTES)]
    result_limit: Annotated[int, Field(strict=True, ge=1, le=limits.MAX_RESULT_LIMIT)]


class RecentRecipeCount(ContractModel):
    recipe_public_id: UUID
    count: Annotated[int, Field(strict=True, ge=1, le=365)]


class RecipeRankingRequest(ContractModel):
    contract_version: Annotated[str, Field(pattern=r"^1$")]
    request_id: UUID
    algorithm_version: AlgorithmVersion
    context: Context
    hard_constraints: HardConstraints
    soft_preferences: SoftPreferences
    nutrition_targets: Annotated[tuple[NutritionTarget, ...],
                                  Field(max_length=limits.MAX_NUTRITION_TARGETS)]
    recent_recipe_counts: Annotated[tuple[RecentRecipeCount, ...],
                                    Field(max_length=limits.MAX_RECENT_RECIPE_COUNTS)]
    unit_definitions: Annotated[tuple[UnitDefinition, ...],
                                Field(max_length=limits.MAX_UNIT_DEFINITIONS)]
    pantry_lots: Annotated[tuple[PantryLot, ...],
                           Field(max_length=limits.MAX_PANTRY_LOTS)]
    ingredient_facts: Annotated[tuple[IngredientFact, ...],
                                Field(max_length=limits.MAX_INGREDIENT_FACTS)]
    recipe_candidates: Annotated[tuple[RecipeCandidate, ...],
                                  Field(max_length=limits.MAX_RECIPE_CANDIDATES)]

    @model_validator(mode="after")
    def identities_and_units_are_safe(self) -> Self:
        _unique(tuple(v.nutrient_code for v in self.nutrition_targets), "nutritionTargets")
        _unique(tuple(v.recipe_public_id for v in self.recent_recipe_counts),
                "recentRecipeCounts")
        _unique(tuple(v.unit_code for v in self.unit_definitions), "unitDefinitions")
        _unique(tuple(v.pantry_item_public_id for v in self.pantry_lots), "pantryLots")
        _unique(tuple(v.ingredient_public_id for v in self.ingredient_facts),
                "ingredientFacts")
        _unique(tuple(v.recipe_public_id for v in self.recipe_candidates),
                "recipeCandidates")

        candidate_ids = {v.recipe_public_id for v in self.recipe_candidates}
        if any(v.recipe_public_id not in candidate_ids for v in self.recent_recipe_counts):
            raise ValueError("recentRecipeCounts must refer only to transported candidates")

        definitions = {v.unit_code: v for v in self.unit_definitions}
        for definition in self.unit_definitions:
            base = definitions.get(definition.base_unit_code)
            if base is None or base.dimension is not definition.dimension:
                raise ValueError("unit definition graph is incomplete or cross-dimensional")
            if base.base_unit_code != base.unit_code or base.to_base_factor != Decimal("1"):
                raise ValueError("base units must be self-referential with factor 1")

        referenced = {v.unit_code for v in self.nutrition_targets}
        referenced.update(v.unit_code for v in self.pantry_lots)
        for recipe in self.recipe_candidates:
            referenced.update(v.unit_code for v in recipe.ingredients if v.unit_code is not None)
            if recipe.nutrition is not None:
                referenced.update(v.unit_code for v in recipe.nutrition.values)
        if not definitions.keys() >= referenced:
            raise ValueError("every referenced unitCode requires a unit definition")
        return self


class RankingScoreComponent(ContractModel):
    component_code: ScoreComponentCode
    value: RankingValue
    weight: PositiveWeight

    @model_validator(mode="after")
    def weight_is_locked(self) -> Self:
        if self.weight != EXPECTED_COMPONENT_WEIGHTS[self.component_code]:
            raise ValueError("score component weight does not match algorithm V1")
        return self


Explanation = Annotated[
    str, StringConstraints(min_length=1, max_length=limits.MAX_EXPLANATION_LENGTH)
]


class RankedRecipe(ContractModel):
    rank: Annotated[int, Field(strict=True, ge=1, le=limits.MAX_RESULT_LIMIT)]
    recipe_public_id: UUID
    total_score: RankingTotal
    score_components: Annotated[tuple[RankingScoreComponent, ...],
                                Field(min_length=7, max_length=7)]
    explanation: Explanation

    @model_validator(mode="after")
    def components_and_total_are_valid(self) -> Self:
        codes = tuple(v.component_code for v in self.score_components)
        if len(set(codes)) != len(codes) or set(codes) != set(ScoreComponentCode):
            raise ValueError("all seven score component codes are required exactly once")
        total = sum(
            (v.value * v.weight *
             (Decimal("-1") if v.component_code is ScoreComponentCode.DISLIKE_PENALTY
              else Decimal("1"))
             for v in self.score_components),
            Decimal("0"),
        ).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
        if total != self.total_score:
            raise ValueError("totalScore must equal the visible weighted component total")
        return self


class RecipeRankingResponse(ContractModel):
    contract_version: Annotated[str, Field(pattern=r"^1$")]
    request_id: UUID
    algorithm_version: AlgorithmVersion
    status: RankingStatus
    ranked_recipes: Annotated[tuple[RankedRecipe, ...],
                              Field(max_length=limits.MAX_RESULT_LIMIT)]
    infeasible_reason: InfeasibleReason | None

    @model_validator(mode="after")
    def outcome_is_valid(self) -> Self:
        if self.status is RankingStatus.SUCCEEDED:
            if not self.ranked_recipes or self.infeasible_reason is not None:
                raise ValueError("SUCCEEDED requires rankedRecipes only")
        elif self.ranked_recipes or self.infeasible_reason is None:
            raise ValueError("INFEASIBLE requires an empty list and a reason")

        seen: set[UUID] = set()
        for rank, item in enumerate(self.ranked_recipes, start=1):
            if item.rank != rank or item.recipe_public_id in seen:
                raise ValueError("ranks must be contiguous and recipe IDs unique")
            seen.add(item.recipe_public_id)
        return self


def _unique(values: tuple[object, ...], field_name: str) -> None:
    if len(set(values)) != len(values):
        raise ValueError(f"{field_name} must not contain duplicates")
