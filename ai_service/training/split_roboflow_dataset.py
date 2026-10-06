"""Create leakage-safe train/val/test splits from a Roboflow train-only export."""

from __future__ import annotations

import argparse
import hashlib
import math
import re
import shutil
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
_AUGMENT_SUFFIX_RE = re.compile(r"_aug\d+$", re.IGNORECASE)
_EXTENSION_TOKEN_RE = re.compile(r"_(?:jpg|jpeg|png|bmp|webp)$", re.IGNORECASE)


@dataclass(frozen=True, slots=True)
class Record:
    image_path: Path
    label_path: Path
    relative_image: Path
    group_key: str
    classes: frozenset[int]


def source_group_key(filename: str) -> str:
    """Return a stable pre-augmentation source identity for a Roboflow filename."""

    stem = Path(filename).stem
    stem = stem.split(".rf.", 1)[0]
    previous = None
    while stem != previous:
        previous = stem
        stem = _EXTENSION_TOKEN_RE.sub("", stem)
        stem = _AUGMENT_SUFFIX_RE.sub("", stem)
    return stem


def _source_names(config: dict[str, Any]) -> dict[int, str]:
    raw = config.get("names")
    if isinstance(raw, list):
        return {index: str(name) for index, name in enumerate(raw)}
    if isinstance(raw, dict):
        return {int(index): str(name) for index, name in raw.items()}
    raise TypeError("data.yaml must contain names as a list or mapping")


def _resolve_train_images(data_yaml: Path, config: dict[str, Any]) -> Path:
    raw = config.get("train")
    if not isinstance(raw, str):
        raise TypeError("data.yaml train must be one directory path")

    configured_root = Path(str(config.get("path", ".")))
    if not configured_root.is_absolute():
        configured_root = data_yaml.parent / configured_root

    train_path = Path(raw)
    if not train_path.is_absolute():
        train_path = configured_root / train_path
    resolved = train_path.resolve()
    if resolved.exists():
        return resolved

    trimmed = [part for part in Path(raw).parts if part not in ("..", ".")]
    fallback = data_yaml.parent.joinpath(*trimmed).resolve()
    if fallback.exists():
        return fallback

    # Common Roboflow ZIP shape when data.yaml says ../train/images.
    direct = (data_yaml.parent / "train" / "images").resolve()
    if direct.exists():
        return direct
    raise FileNotFoundError(f"Training images directory does not exist: {resolved}")


def _labels_dir(images_dir: Path) -> Path:
    parts = list(images_dir.parts)
    candidates = [index for index, part in enumerate(parts) if part.lower() == "images"]
    if not candidates:
        raise ValueError(f"Cannot derive labels directory from {images_dir}")
    parts[candidates[-1]] = "labels"
    return Path(*parts)


def _label_classes(label_path: Path, class_count: int) -> frozenset[int]:
    classes: set[int] = set()
    if not label_path.exists():
        return frozenset()
    for line_number, raw_line in enumerate(
        label_path.read_text(encoding="utf-8").splitlines(), 1
    ):
        fields = raw_line.strip().split()
        if not fields:
            continue
        if len(fields) != 5:
            raise ValueError(f"{label_path}:{line_number}: expected 5 YOLO fields")
        try:
            class_id = int(fields[0])
        except ValueError as exc:
            raise ValueError(f"{label_path}:{line_number}: invalid class id") from exc
        if class_id < 0 or class_id >= class_count:
            raise ValueError(
                f"{label_path}:{line_number}: class id {class_id} outside 0..{class_count - 1}"
            )
        classes.add(class_id)
    return frozenset(classes)


def collect_records(data_yaml: Path) -> tuple[list[Record], dict[int, str]]:
    config = yaml.safe_load(data_yaml.read_text(encoding="utf-8"))
    if not isinstance(config, dict):
        raise TypeError("data.yaml must decode to a mapping")

    names = _source_names(config)
    images_dir = _resolve_train_images(data_yaml, config)
    labels_dir = _labels_dir(images_dir)

    records: list[Record] = []
    for image_path in sorted(images_dir.rglob("*")):
        if not image_path.is_file() or image_path.suffix.lower() not in IMAGE_SUFFIXES:
            continue
        relative = image_path.relative_to(images_dir)
        label_path = (labels_dir / relative).with_suffix(".txt")
        if not label_path.exists():
            raise FileNotFoundError(f"Missing YOLO label for image: {image_path}")
        records.append(
            Record(
                image_path=image_path,
                label_path=label_path,
                relative_image=relative,
                group_key=source_group_key(image_path.name),
                classes=_label_classes(label_path, len(names)),
            )
        )

    if not records:
        raise ValueError("No images found in source training directory")
    return records, names


def split_groups(
    records: list[Record],
    *,
    train_ratio: float = 0.70,
    val_ratio: float = 0.15,
    test_ratio: float = 0.15,
    seed: int = 42,
) -> dict[str, list[Record]]:
    """Split source groups while balancing class presence and preventing leakage."""

    if not math.isclose(train_ratio + val_ratio + test_ratio, 1.0, abs_tol=1e-9):
        raise ValueError("Split ratios must sum to 1")

    grouped: dict[str, list[Record]] = defaultdict(list)
    for record in records:
        grouped[record.group_key].append(record)

    if len(grouped) < 3:
        raise ValueError("At least three source groups are required")

    split_names = ("train", "val", "test")
    ratios = {"train": train_ratio, "val": val_ratio, "test": test_ratio}
    total_groups = len(grouped)
    target_groups = {
        name: max(1, round(total_groups * ratios[name])) for name in split_names
    }
    while sum(target_groups.values()) > total_groups:
        largest = max(split_names, key=lambda name: target_groups[name])
        if target_groups[largest] <= 1:
            break
        target_groups[largest] -= 1
    while sum(target_groups.values()) < total_groups:
        target_groups["train"] += 1

    group_classes: dict[str, frozenset[int]] = {
        key: frozenset(
            class_id for record in group_records for class_id in record.classes
        )
        for key, group_records in grouped.items()
    }
    total_class_groups: Counter[int] = Counter(
        class_id for classes in group_classes.values() for class_id in classes
    )
    target_class_groups = {
        split: {
            class_id: total * ratios[split]
            for class_id, total in total_class_groups.items()
        }
        for split in split_names
    }

    def tie_value(key: str) -> str:
        return hashlib.sha256(f"{seed}:{key}".encode()).hexdigest()

    ordered_keys = sorted(
        grouped,
        key=lambda key: (
            -sum(
                1.0 / total_class_groups[class_id]
                for class_id in group_classes[key]
                if total_class_groups[class_id]
            ),
            tie_value(key),
        ),
    )

    assignments: dict[str, str] = {}
    split_group_count: Counter[str] = Counter()
    split_class_groups: dict[str, Counter[int]] = {
        name: Counter() for name in split_names
    }

    for key in ordered_keys:
        classes = group_classes[key]
        candidates = [
            name
            for name in split_names
            if split_group_count[name] < target_groups[name]
        ] or list(split_names)

        def score(split: str) -> tuple[float, float, str]:
            class_need = 0.0
            for class_id in classes:
                target = target_class_groups[split][class_id]
                if target > 0:
                    deficit = max(target - split_class_groups[split][class_id], 0.0)
                    class_need += deficit / target
            size_target = target_groups[split]
            size_need = max(size_target - split_group_count[split], 0) / size_target
            return (class_need + 0.35 * size_need, size_need, tie_value(f"{key}:{split}"))

        chosen = max(candidates, key=score)
        assignments[key] = chosen
        split_group_count[chosen] += 1
        split_class_groups[chosen].update(classes)

    split_records = {name: [] for name in split_names}
    for key, group_records in grouped.items():
        split_records[assignments[key]].extend(group_records)

    group_sets = {
        split: {record.group_key for record in items}
        for split, items in split_records.items()
    }
    if group_sets["train"] & group_sets["val"]:
        raise AssertionError("Group leakage between train and val")
    if group_sets["train"] & group_sets["test"]:
        raise AssertionError("Group leakage between train and test")
    if group_sets["val"] & group_sets["test"]:
        raise AssertionError("Group leakage between val and test")

    return split_records


def write_split(
    split_records: dict[str, list[Record]],
    names: dict[int, str],
    output_root: Path,
) -> None:
    if output_root.exists():
        shutil.rmtree(output_root)

    for split, records in split_records.items():
        image_root = output_root / split / "images"
        label_root = output_root / split / "labels"
        image_root.mkdir(parents=True, exist_ok=True)
        label_root.mkdir(parents=True, exist_ok=True)

        for record in records:
            image_target = image_root / record.relative_image
            label_target = (label_root / record.relative_image).with_suffix(".txt")
            image_target.parent.mkdir(parents=True, exist_ok=True)
            label_target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(record.image_path, image_target)
            shutil.copy2(record.label_path, label_target)

    yaml_payload = {
        "path": str(output_root.resolve()),
        "train": "train/images",
        "val": "val/images",
        "test": "test/images",
        "names": names,
    }
    (output_root / "data.yaml").write_text(
        yaml.safe_dump(yaml_payload, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )


def print_audit(split_records: dict[str, list[Record]], names: dict[int, str]) -> None:
    print("Leakage-safe Roboflow split created")
    all_groups = {
        record.group_key for records in split_records.values() for record in records
    }
    print(f"Source groups: {len(all_groups)}")
    for split in ("train", "val", "test"):
        records = split_records[split]
        groups = {record.group_key for record in records}
        class_presence: Counter[int] = Counter(
            class_id for record in records for class_id in record.classes
        )
        print(f"{split}: {len(groups)} groups, {len(records)} images")
        for class_id, class_name in names.items():
            print(
                f"  {class_id:>2} {class_name:<16} "
                f"{class_presence[class_id]:>5} labelled images"
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-yaml", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("ai_service/training/datasets/ingredients_split_raw"),
    )
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    records, names = collect_records(args.source_yaml.resolve())
    split_records = split_groups(records, seed=args.seed)
    write_split(split_records, names, args.output.resolve())
    print_audit(split_records, names)
    print(f"Output: {args.output.resolve()}")


if __name__ == "__main__":
    main()
