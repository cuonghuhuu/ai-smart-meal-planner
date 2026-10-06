from app.food_recognition.catalog import (
    INGREDIENT_CLASSES,
    map_source_classes,
    vietnamese_names,
)


def test_rescue_catalog_has_twelve_dense_vietnamese_classes() -> None:
    assert [item.class_id for item in INGREDIENT_CLASSES] == list(range(12))
    assert vietnamese_names()[0] == "Trứng gà"
    assert vietnamese_names()[8] == "Thịt lợn"
    assert vietnamese_names()[11] == "Bắp cải"


def test_source_class_mapping_is_independent_of_source_order() -> None:
    source_names = {
        7: "fish",
        2: "tomato",
        21: "carrot",
        6: "chicken",
        11: "egg",
        4: "potato",
        30: "onion",
        10: "cucumber",
        12: "cabbage",
        9: "garlic",
        42: "beef",
        1: "pork",
    }

    mapped = map_source_classes(source_names)

    assert mapped[11].name_vi == "Trứng gà"
    assert mapped[2].class_id == 1
    assert mapped[1].code == "THIT_LON"


def test_source_mapping_fails_when_a_promised_class_is_missing() -> None:
    source_names = {
        item.class_id: item.source_aliases[0]
        for item in INGREDIENT_CLASSES
        if item.code != "THIT_BO"
    }

    try:
        map_source_classes(source_names)
    except ValueError as exc:
        assert "Thịt bò" in str(exc)
    else:
        raise AssertionError("Expected fail-closed mapping for a missing target class")
