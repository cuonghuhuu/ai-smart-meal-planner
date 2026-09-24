"""Immutable, lot-level Pantry simulation; no database or real-Pantry writes."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from types import MappingProxyType
from typing import Mapping
from uuid import UUID

from app.meal_planning.contracts import (
    IngredientRequirement,
    MealPlanGenerationRequest,
    PantryLot,
    UnitDefinition,
)

ZERO = Decimal("0")
ONE = Decimal("1")
EXPIRY_HORIZON_DAYS = 7


@dataclass(frozen=True, slots=True)
class UnitCatalog:
    """Only the same-dimension relations validated by the V1 contract."""

    definitions: Mapping[str, UnitDefinition]

    @classmethod
    def from_request(cls, request: MealPlanGenerationRequest) -> UnitCatalog:
        return cls(MappingProxyType({unit.unit_code: unit for unit in request.unit_definitions}))

    def compatible(self, first: str, second: str) -> bool:
        left = self.definitions[first]
        right = self.definitions[second]
        return left.dimension is right.dimension and left.base_unit_code == right.base_unit_code

    def to_base(self, quantity: Decimal, unit_code: str) -> Decimal:
        return quantity * self.definitions[unit_code].to_base_factor


@dataclass(frozen=True, slots=True)
class PantryUse:
    lot_public_id: UUID
    ingredient_public_id: UUID
    quantity_base: Decimal
    expiry_date: date | None


@dataclass(frozen=True, slots=True)
class PantrySimulation:
    pantry: VirtualPantry
    coverage: Decimal
    expiry_urgency: Decimal
    missing_ingredients: int
    uses: tuple[PantryUse, ...]


@dataclass(frozen=True, slots=True)
class VirtualPantry:
    """Balances are in each lot's dimension base unit, independently per branch."""

    lots: tuple[PantryLot, ...]
    remaining_base: tuple[Decimal, ...]
    units: UnitCatalog
    ingredient_indexes: Mapping[UUID, tuple[int, ...]]

    @classmethod
    def from_request(cls, request: MealPlanGenerationRequest) -> VirtualPantry:
        units = UnitCatalog.from_request(request)
        indexes: dict[UUID, list[int]] = {}
        for index, lot in enumerate(request.pantry_lots):
            indexes.setdefault(lot.ingredient_public_id, []).append(index)
        for entries in indexes.values():
            entries.sort(key=lambda index: _lot_sort_key(request.pantry_lots[index]))
        return cls(
            lots=request.pantry_lots,
            remaining_base=tuple(
                units.to_base(lot.quantity_remaining, lot.unit_code)
                for lot in request.pantry_lots
            ),
            units=units,
            ingredient_indexes=MappingProxyType(
                {ingredient: tuple(entries) for ingredient, entries in indexes.items()}
            ),
        )

    def simulate(
        self,
        requirements: tuple[IngredientRequirement, ...],
        serving_scale: Decimal,
        plan_date: date,
    ) -> PantrySimulation:
        """Use available compatible lots, recording shortages without overdraw."""

        balances = list(self.remaining_base)
        coverage_fractions: list[Decimal] = []
        urgency_fractions: list[Decimal] = []
        uses: list[PantryUse] = []
        missing = 0
        for requirement in requirements:
            if requirement.quantity is None or requirement.unit_code is None:
                # A mandatory unknown amount is rejected by the CSP layer.
                continue
            needed = self.units.to_base(requirement.quantity * serving_scale, requirement.unit_code)
            consumed = ZERO
            urgency_weighted = ZERO
            for index in self.ingredient_indexes.get(requirement.ingredient_public_id, ()):
                lot = self.lots[index]
                if lot.expiry_date is not None and lot.expiry_date < plan_date:
                    continue
                if not self.units.compatible(lot.unit_code, requirement.unit_code):
                    continue
                take = min(needed - consumed, balances[index])
                if take <= ZERO:
                    continue
                balances[index] -= take
                consumed += take
                urgency_weighted += take * _expiry_value(lot.expiry_date, plan_date)
                uses.append(PantryUse(
                    lot_public_id=lot.pantry_item_public_id,
                    ingredient_public_id=lot.ingredient_public_id,
                    quantity_base=take,
                    expiry_date=lot.expiry_date,
                ))
                if consumed == needed:
                    break
            if not requirement.optional:
                coverage_fractions.append(consumed / needed)
                urgency_fractions.append(urgency_weighted / needed)
                if consumed < needed:
                    missing += 1
        coverage = (
            sum(coverage_fractions, ZERO) / len(coverage_fractions)
            if coverage_fractions else ONE
        )
        urgency = (
            sum(urgency_fractions, ZERO) / len(urgency_fractions)
            if urgency_fractions else ZERO
        )
        return PantrySimulation(
            pantry=VirtualPantry(self.lots, tuple(balances), self.units, self.ingredient_indexes),
            coverage=coverage,
            expiry_urgency=urgency,
            missing_ingredients=missing,
            uses=tuple(uses),
        )


def _lot_sort_key(lot: PantryLot) -> tuple[date, int, str]:
    return (
        lot.expiry_date or date.max,
        0 if lot.expiry_kind.value == "USE_BY" else 1,
        str(lot.pantry_item_public_id),
    )


def _expiry_value(expiry_date: date | None, plan_date: date) -> Decimal:
    if expiry_date is None:
        return ZERO
    remaining_days = (expiry_date - plan_date).days
    if remaining_days >= EXPIRY_HORIZON_DAYS:
        return ZERO
    return Decimal(EXPIRY_HORIZON_DAYS - remaining_days) / EXPIRY_HORIZON_DAYS
