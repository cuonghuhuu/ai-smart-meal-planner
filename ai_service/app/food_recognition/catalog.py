"""Stable recognition class catalog shared by training and runtime mapping."""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class IngredientClass:
    """One detector class with a stable machine code and Vietnamese display name."""

    class_id: int
    code: str
    name_vi: str
    source_aliases: tuple[str, ...]


def _normalize(value: str) -> str:
    return " ".join(value.strip().lower().replace("_", " ").replace("-", " ").split())


INGREDIENT_CLASSES: tuple[IngredientClass, ...] = (
    IngredientClass(0, "TRUNG_GA", "Trứng gà", ("egg", "eggs", "chicken egg")),
    IngredientClass(1, "CA_CHUA", "Cà chua", ("tomato", "tomatoes")),
    IngredientClass(2, "CA_ROT", "Cà rốt", ("carrot", "carrots")),
    IngredientClass(3, "HANH_TAY", "Hành tây", ("onion", "onions")),
    IngredientClass(4, "TOI", "Tỏi", ("garlic",)),
    IngredientClass(5, "DUA_CHUOT", "Dưa chuột", ("cucumber", "cucumbers")),
    IngredientClass(6, "KHOAI_TAY", "Khoai tây", ("potato", "potatoes")),
    IngredientClass(7, "THIT_GA", "Thịt gà", ("chicken", "chicken meat")),
    IngredientClass(8, "THIT_LON", "Thịt lợn", ("pork", "pork meat")),
    IngredientClass(9, "THIT_BO", "Thịt bò", ("beef", "beef meat")),
    IngredientClass(10, "CA", "Cá", ("fish",)),
    IngredientClass(11, "BAP_CAI", "Bắp cải", ("cabbage",)),
)

INGREDIENT_CLASS_BY_ID: Mapping[int, IngredientClass] = {
    item.class_id: item for item in INGREDIENT_CLASSES
}
INGREDIENT_CLASS_BY_CODE: Mapping[str, IngredientClass] = {
    item.code: item for item in INGREDIENT_CLASSES
}


def vietnamese_names() -> dict[int, str]:
    """Return the exact class-id to Vietnamese-name mapping written to data.yaml."""

    return {item.class_id: item.name_vi for item in INGREDIENT_CLASSES}


def map_source_classes(source_names: Mapping[int, str]) -> dict[int, IngredientClass]:
    """Map source dataset ids onto the twelve rescue detector classes, failing closed."""

    alias_to_target: dict[str, IngredientClass] = {}
    for target in INGREDIENT_CLASSES:
        for alias in target.source_aliases:
            alias_to_target[_normalize(alias)] = target

    mapped: dict[int, IngredientClass] = {}
    found_target_ids: set[int] = set()
    for source_id, source_name in source_names.items():
        target = alias_to_target.get(_normalize(source_name))
        if target is not None:
            mapped[source_id] = target
            found_target_ids.add(target.class_id)

    missing = [
        item.name_vi for item in INGREDIENT_CLASSES if item.class_id not in found_target_ids
    ]
    if missing:
        raise ValueError(
            "Source dataset is missing required rescue classes: " + ", ".join(missing)
        )
    return mapped


def validate_dense_ids(classes: Iterable[IngredientClass] = INGREDIENT_CLASSES) -> None:
    """Assert that detector ids are dense and start at zero."""

    ids = [item.class_id for item in classes]
    expected = list(range(len(ids)))
    if ids != expected:
        raise ValueError(f"Ingredient class ids must be dense: expected {expected}, got {ids}")


validate_dense_ids()
