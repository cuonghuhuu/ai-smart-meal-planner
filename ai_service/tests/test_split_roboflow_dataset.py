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
