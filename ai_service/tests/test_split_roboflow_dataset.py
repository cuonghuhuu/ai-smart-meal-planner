from pathlib import Path

from ai_service.training.split_roboflow_dataset import Record, source_group_key, split_groups


def _record(name: str, classes: set[int]) -> Record:
    path = Path(name)
    return Record(
        image_path=path,
        label_path=path.with_suffix(".txt"),
        relative_image=path,
        group_key=source_group_key(name),
        classes=frozenset(classes),
    )


def test_source_group_key_collapses_roboflow_hash_and_augmentation_variants() -> None:
    assert source_group_key("-11_jpg.rf.abc123.jpg") == "-11"
    assert source_group_key("-11_jpg_aug0_jpg.rf.def456.jpg") == "-11"
    assert source_group_key("-11_jpg_aug3_jpg.rf.fff999.jpg") == "-11"


def test_split_groups_never_leaks_source_group_across_splits() -> None:
    records = []
    for index in range(30):
        for variant in range(3):
            records.append(
                _record(
                    f"food{index}_jpg_aug{variant}_jpg.rf.hash{index}{variant}.jpg",
                    {index % 4},
                )
            )

    split = split_groups(records, seed=42)
    group_sets = {
        name: {record.group_key for record in items}
        for name, items in split.items()
    }

    assert group_sets["train"].isdisjoint(group_sets["val"])
    assert group_sets["train"].isdisjoint(group_sets["test"])
    assert group_sets["val"].isdisjoint(group_sets["test"])
    assert sum(len(items) for items in split.values()) == len(records)


def test_split_groups_keeps_single_source_class_in_train() -> None:
    records = [
        _record("rare_jpg.rf.a.jpg", {9}),
        _record("common1_jpg.rf.a.jpg", {0}),
        _record("common2_jpg.rf.a.jpg", {0}),
        _record("common3_jpg.rf.a.jpg", {0}),
        _record("common4_jpg.rf.a.jpg", {0}),
        _record("common5_jpg.rf.a.jpg", {0}),
    ]

    split = split_groups(records, seed=42)

    assert any(9 in record.classes for record in split["train"])
    assert all(9 not in record.classes for record in split["val"])
    assert all(9 not in record.classes for record in split["test"])


def test_split_groups_spreads_class_across_all_splits_when_three_groups_exist() -> None:
    records = [
        _record("rare1_jpg.rf.a.jpg", {7}),
        _record("rare2_jpg.rf.a.jpg", {7}),
        _record("rare3_jpg.rf.a.jpg", {7}),
        _record("common1_jpg.rf.a.jpg", {0}),
        _record("common2_jpg.rf.a.jpg", {0}),
        _record("common3_jpg.rf.a.jpg", {0}),
        _record("common4_jpg.rf.a.jpg", {0}),
        _record("common5_jpg.rf.a.jpg", {0}),
        _record("common6_jpg.rf.a.jpg", {0}),
    ]

    split = split_groups(records, seed=42)

    for split_name in ("train", "val", "test"):
        assert any(7 in record.classes for record in split[split_name])
