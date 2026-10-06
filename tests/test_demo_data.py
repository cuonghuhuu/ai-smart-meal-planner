"""Cross-import checks for the explicit local demo dataset."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEMO = ROOT / "database" / "demo"
YOLO_CODES = {
    "TRUNG_GA",
    "CA_CHUA",
    "CA_ROT",
    "HANH_TAY",
    "TOI",
    "DUA_CHUOT",
    "KHOAI_TAY",
    "THIT_GA",
    "THIT_LON",
    "THIT_BO",
    "CA",
    "BAP_CAI",
}


def test_demo_catalog_and_recipes_share_real_import_identities():
    catalog = json.loads((DEMO / "catalog-v1.json").read_text(encoding="utf-8"))
    recipes = json.loads((DEMO / "recipes-v1.json").read_text(encoding="utf-8"))
    foods = catalog["foods"]
    items = recipes["recipes"]
    ingredient_codes = {food["ingredientMapping"]["ingredientCode"] for food in foods}
    assert len(foods) == 16
    assert len(items) == 18
    assert {f"DEMO_ING_{code}" for code in YOLO_CODES} <= ingredient_codes
    assert len(ingredient_codes) == len(foods)
    assert all(food["source"] == "IMPORTED" for food in foods)
    assert all(
        {fact["canonicalCode"] for fact in food["nutrientFacts"]}
        == {"ENERGY", "PROTEIN", "FAT_TOTAL", "CARBOHYDRATE"}
        and all(fact["dataQuality"] == "ESTIMATED" for fact in food["nutrientFacts"])
        for food in foods
    )
    assert all(
        line["ingredientCode"] in ingredient_codes
        for recipe in items
        for line in recipe["ingredients"]
    )
    assert len({recipe["sourceReference"] for recipe in items}) == len(items)
    assert {slot for recipe in items for slot in recipe["mealSlots"]} >= {
        "BREAKFAST",
        "LUNCH",
        "DINNER",
    }
