"""Build explicit, deterministic local demo imports for the existing Java importers.

Nutrition values are approximate teaching/demo estimates per 100 g, never
represented as SMILING or clinical data. No application database is accessed.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "database" / "demo"

# key, Vietnamese catalog name, category, kcal, protein, fat, carbohydrate
FOODS = (
    ("TRUNG_GA", "Trứng gà", "PROTEIN_EGG", 143, 13, 10, 1),
    ("CA_CHUA", "Cà chua", "VEGETABLES", 18, 1, 0, 4),
    ("CA_ROT", "Cà rốt", "VEG_ROOT", 41, 1, 0, 10),
    ("HANH_TAY", "Hành tây", "VEG_ALLIUM", 40, 1, 0, 9),
    ("TOI", "Tỏi", "VEG_ALLIUM", 149, 6, 1, 33),
    ("DUA_CHUOT", "Dưa chuột", "VEGETABLES", 15, 1, 0, 4),
    ("KHOAI_TAY", "Khoai tây", "VEG_ROOT", 77, 2, 0, 17),
    ("THIT_GA", "Thịt gà", "PROTEIN_POULTRY", 165, 31, 4, 0),
    ("THIT_LON", "Thịt lợn", "PROTEIN_MEAT", 242, 27, 14, 0),
    ("THIT_BO", "Thịt bò", "PROTEIN_MEAT", 250, 26, 15, 0),
    ("CA", "Cá", "PROTEIN_SEAFOOD", 128, 20, 5, 0),
    ("BAP_CAI", "Bắp cải", "VEG_LEAFY", 25, 1, 0, 6),
    ("GAO_TE", "Gạo tẻ", "GRAINS_RICE", 365, 7, 1, 80),
    ("DAU_PHU", "Đậu phụ", "PROTEIN_LEGUME", 76, 8, 5, 2),
    ("RAU_MUONG", "Rau muống", "VEG_LEAFY", 19, 3, 0, 3),
    ("DAU_AN", "Dầu ăn", "FATS_OILS", 884, 0, 100, 0),
)

# title, meal slots, base ingredients (demo code, grams), tags
RECIPES = (
    (
        "Trứng luộc và cơm",
        ("BREAKFAST", "LUNCH"),
        (("TRUNG_GA", 100), ("GAO_TE", 80)),
        ("MAIN_DISH",),
    ),
    (
        "Cháo gà cà rốt",
        ("BREAKFAST",),
        (("GAO_TE", 70), ("THIT_GA", 100), ("CA_ROT", 70)),
        ("SOUP",),
    ),
    (
        "Trứng chiên cà chua",
        ("BREAKFAST", "LUNCH"),
        (("TRUNG_GA", 100), ("CA_CHUA", 100), ("DAU_AN", 8)),
        ("MAIN_DISH",),
    ),
    (
        "Canh bắp cải thịt lợn",
        ("LUNCH", "DINNER"),
        (("BAP_CAI", 150), ("THIT_LON", 100)),
        ("SOUP",),
    ),
    (
        "Cơm thịt gà hành tây",
        ("LUNCH", "DINNER"),
        (("GAO_TE", 90), ("THIT_GA", 140), ("HANH_TAY", 60)),
        ("MAIN_DISH",),
    ),
    (
        "Cá hấp tỏi",
        ("LUNCH", "DINNER"),
        (("CA", 160), ("TOI", 8), ("GAO_TE", 80)),
        ("STEAMED",),
    ),
    (
        "Bò xào cà rốt",
        ("LUNCH", "DINNER"),
        (("THIT_BO", 140), ("CA_ROT", 90), ("DAU_AN", 8)),
        ("STIR_FRIED",),
    ),
    (
        "Canh cà chua trứng",
        ("BREAKFAST", "LUNCH"),
        (("CA_CHUA", 150), ("TRUNG_GA", 100)),
        ("SOUP",),
    ),
    (
        "Đậu phụ xào bắp cải",
        ("LUNCH", "DINNER"),
        (("DAU_PHU", 150), ("BAP_CAI", 150), ("DAU_AN", 8)),
        ("VEGETARIAN",),
    ),
    (
        "Khoai tây gà áp chảo",
        ("LUNCH", "DINNER"),
        (("KHOAI_TAY", 180), ("THIT_GA", 130)),
        ("MAIN_DISH",),
    ),
    (
        "Cơm cá cà chua",
        ("LUNCH", "DINNER"),
        (("GAO_TE", 90), ("CA", 140), ("CA_CHUA", 100)),
        ("MAIN_DISH",),
    ),
    (
        "Cháo thịt lợn rau muống",
        ("BREAKFAST",),
        (("GAO_TE", 70), ("THIT_LON", 100), ("RAU_MUONG", 80)),
        ("SOUP",),
    ),
    (
        "Salad dưa chuột trứng",
        ("BREAKFAST", "LUNCH"),
        (("DUA_CHUOT", 150), ("TRUNG_GA", 100), ("CA_CHUA", 100)),
        ("SALAD",),
    ),
    (
        "Bò hầm khoai tây",
        ("LUNCH", "DINNER"),
        (("THIT_BO", 140), ("KHOAI_TAY", 170), ("HANH_TAY", 50)),
        ("MAIN_DISH",),
    ),
    (
        "Canh cá rau muống",
        ("LUNCH", "DINNER"),
        (("CA", 140), ("RAU_MUONG", 120)),
        ("SOUP",),
    ),
    (
        "Cơm đậu phụ cà rốt",
        ("LUNCH", "DINNER"),
        (("GAO_TE", 85), ("DAU_PHU", 160), ("CA_ROT", 90)),
        ("VEGETARIAN",),
    ),
    (
        "Gà xào bắp cải",
        ("LUNCH", "DINNER"),
        (("THIT_GA", 140), ("BAP_CAI", 170), ("TOI", 8)),
        ("STIR_FRIED",),
    ),
    (
        "Cơm thịt lợn cà chua",
        ("LUNCH", "DINNER"),
        (("GAO_TE", 90), ("THIT_LON", 130), ("CA_CHUA", 120)),
        ("MAIN_DISH",),
    ),
)


def catalog_document() -> dict:
    foods = []
    for code, name, category, energy, protein, fat, carbs in FOODS:
        nutrients = (
            ("ENERGY", energy, "kcal"),
            ("PROTEIN", protein, "g"),
            ("FAT_TOTAL", fat, "g"),
            ("CARBOHYDRATE", carbs, "g"),
        )
        foods.append(
            {
                "sourceIdentifier": f"LOCAL_DEMO_{code}",
                "catalogCode": f"DEMO_FOOD_{code}",
                "displayName": name,
                "sourceName": None,
                "categoryCode": category,
                "description": "Dữ liệu mẫu địa phương; giá trị dinh dưỡng ước tính.",
                "nutritionBasis": "PER_100_G",
                "densityGPerMl": None,
                "source": "IMPORTED",
                "sourceReference": f"AI_MEAL_PLANNER_LOCAL_DEMO_V1:{code}",
                "nutrientFacts": [
                    {
                        "sourceCode": nutrient.lower(),
                        "canonicalCode": nutrient,
                        "amount": amount,
                        "unitCode": unit,
                        "dataQuality": "ESTIMATED",
                    }
                    for nutrient, amount, unit in nutrients
                ],
                "ingredientMapping": {
                    "ingredientCode": f"DEMO_ING_{code}",
                    "displayName": name,
                    "categoryCode": category,
                    "defaultUnitCode": "g",
                    "preparationState": "RAW",
                    "yieldFactor": 1,
                    "primary": True,
                    "aliases": [],
                },
            }
        )
    return {
        "dataset": "AI Smart Meal Planner local demonstration catalog",
        "datasetVersion": "v1",
        "foods": foods,
    }


def recipe_document() -> dict:
    recipes = []
    for index, (title, slots, ingredients, tags) in enumerate(RECIPES, 1):
        identifier = f"LOCAL_DEMO_{index:03d}"
        recipes.append(
            {
                "sourceIdentifier": identifier,
                "title": title,
                "slug": f"local-demo-{index:03d}",
                "summary": f"{title}. Công thức mẫu cho buổi trình diễn.",
                "servings": 1,
                "prepMinutes": 10,
                "cookMinutes": 20,
                "difficulty": "EASY",
                "instructionsNote": "Điều chỉnh gia vị và độ chín theo thực phẩm thực tế.",
                "imageUrl": None,
                "source": "CURATED",
                "sourceReference": f"AI_MEAL_PLANNER_LOCAL_DEMO_V1:{identifier}",
                "tags": ["VIETNAMESE", *tags],
                "mealSlots": list(slots),
                "ingredients": [
                    {
                        "lineNumber": line,
                        "ingredientCode": f"DEMO_ING_{code}",
                        "quantity": grams,
                        "unitCode": "g",
                        "preparationNote": None,
                        "optional": False,
                        "allowSubstitution": False,
                        "sectionLabel": "Nguyên liệu",
                    }
                    for line, (code, grams) in enumerate(ingredients, 1)
                ],
                "steps": [
                    {
                        "stepNumber": 1,
                        "instruction": "Sơ chế nguyên liệu, rửa sạch và chia đúng khẩu phần.",
                        "durationMinutes": 10,
                    },
                    {
                        "stepNumber": 2,
                        "instruction": "Nấu chín nguyên liệu phù hợp với tên món, rồi dùng ngay.",
                        "durationMinutes": 20,
                    },
                ],
            }
        )
    return {
        "dataset": "AI Smart Meal Planner local demonstration recipes",
        "datasetVersion": "v1",
        "recipes": recipes,
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, document in (
        ("catalog-v1.json", catalog_document()),
        ("recipes-v1.json", recipe_document()),
    ):
        (OUT / name).write_text(
            json.dumps(document, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    print(
        f"Built {len(FOODS)} catalog foods/ingredients and {len(RECIPES)} recipes in {OUT}"
    )


if __name__ == "__main__":
    main()
