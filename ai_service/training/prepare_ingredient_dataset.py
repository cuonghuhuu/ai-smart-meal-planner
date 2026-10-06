"""Filter and remap an Ultralytics YOLO dataset to the Vietnamese rescue classes."""

from __future__ import annotations

import argparse
import shutil
import sys
from collections import Counter
from pathlib import Path
from typing import Any

import yaml

AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(AI_SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_SERVICE_ROOT))

from app.food_recognition.catalog import INGREDIENT_CLASSES, map_source_classes, vietnamese_names

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def _source_names(config: dict[str, Any]) -> dict[int, str]:
    raw = config.get("names")
    if isinstance(raw, list):
        return {index: str(name) for index, name in enumerate(raw)}
    if isinstance(raw, dict):
        return {int(index): str(name) for index, name in raw.items()}
    raise TypeError("Source data.yaml must contain names as a list or mapping")


def _resolve_split(data_yaml: Path, config: dict[str, Any], key: str) -> Path | None:
    raw = config.get(key)
    if raw is None:
        return None
    if not isinstance(raw, str):
        raise TypeError(f"Only one directory per split is supported for {key!r}")

    configured_root = Path(str(config.get("path", ".")))
    if not configured_root.is_absolute():
        configured_root = data_yaml.parent / configured_root

    split_path = Path(raw)
    if not split_path.is_absolute():
        split_path = configured_root / split_path
    resolved = split_path.resolve()
    if resolved.exists():
        return resolved

    trimmed_parts = [part for part in Path(raw).parts if part not in ("..", ".")]
    fallback = data_yaml.parent.joinpath(*trimmed_parts).resolve()
    return fallback if fallback.exists() else resolved


def _labels_dir(images_dir: Path) -> Path:
    parts = list(images_dir.parts)
    candidates = [index for index, part in enumerate(parts) if part.lower() == "images"]
    if not candidates:
        sibling = images_dir.parent / "labels"
        if sibling.exists():
            return sibling
        raise ValueError(f"Cannot derive labels directory from {images_dir}")
    parts[candidates[-1]] = "labels"
    return Path(*parts)


def _iter_images(images_dir: Path):
    for path in sorted(images_dir.rglob("*")):
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
            yield path


def _remap_label_file(
    label_path: Path, source_to_target: dict[int, Any]
) -> tuple[list[str], Counter[int]]:
    output: list[str] = []
    counts: Counter[int] = Counter()
    if not label_path.exists():
        return output, counts

    for line_number, raw_line in enumerate(
        label_path.read_text(encoding="utf-8").splitlines(), 1
    ):
        stripped = raw_line.strip()
        if not stripped:
            continue
        fields = stripped.split()
        if len(fields) != 5:
            raise ValueError(f"{label_path}:{line_number}: expected 5 YOLO fields")
        try:
            source_id = int(fields[0])
            coords = [float(value) for value in fields[1:]]
        except ValueError as exc:
            raise ValueError(
                f"{label_path}:{line_number}: invalid numeric YOLO label"
            ) from exc
        if any(value < 0.0 or value > 1.0 for value in coords):
            raise ValueError(
                f"{label_path}:{line_number}: coordinates must be normalized to 0..1"
            )

        target = source_to_target.get(source_id)
        if target is None:
            continue
        output.append(" ".join([str(target.class_id), *fields[1:]]))
        counts[target.class_id] += 1
    return output, counts


def prepare(source_yaml: Path, output_root: Path) -> None:
    config = yaml.safe_load(source_yaml.read_text(encoding="utf-8"))
    if not isinstance(config, dict):
        raise TypeError("Source data.yaml must decode to a mapping")

    source_to_target = map_source_classes(_source_names(config))
    if output_root.exists():
        shutil.rmtree(output_root)

    split_keys = ("train", "val", "test")
    copied_images = Counter()
    object_counts: Counter[int] = Counter()

    for split in split_keys:
        source_images = _resolve_split(source_yaml, config, split)
        if source_images is None:
            continue
        if not source_images.exists():
            raise FileNotFoundError(
                f"{split} images directory does not exist: {source_images}"
            )
        source_labels = _labels_dir(source_images)

        destination_images = output_root / "images" / split
        destination_labels = output_root / "labels" / split
        destination_images.mkdir(parents=True, exist_ok=True)
        destination_labels.mkdir(parents=True, exist_ok=True)

        for image_path in _iter_images(source_images):
            relative = image_path.relative_to(source_images)
            label_path = (source_labels / relative).with_suffix(".txt")
            remapped, counts = _remap_label_file(label_path, source_to_target)
            if not remapped:
                continue

            image_target = destination_images / relative
            label_target = (destination_labels / relative).with_suffix(".txt")
            image_target.parent.mkdir(parents=True, exist_ok=True)
            label_target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(image_path, image_target)
            label_target.write_text("\n".join(remapped) + "\n", encoding="utf-8")
            copied_images[split] += 1
            object_counts.update(counts)

    if copied_images["train"] == 0 or copied_images["val"] == 0:
        raise ValueError("Prepared dataset must contain non-empty train and val splits")
    missing_objects = [
        item.name_vi for item in INGREDIENT_CLASSES if object_counts[item.class_id] == 0
    ]
    if missing_objects:
        raise ValueError("No retained objects for: " + ", ".join(missing_objects))

    dataset_yaml = {
        "path": str(output_root.resolve()),
        "train": "images/train",
        "val": "images/val",
        "test": "images/test" if copied_images["test"] else None,
        "names": vietnamese_names(),
    }
    if dataset_yaml["test"] is None:
        dataset_yaml.pop("test")
    (output_root / "data.yaml").write_text(
        yaml.safe_dump(dataset_yaml, allow_unicode=True, sort_keys=False),
        encoding="utf-8",
    )

    print("Prepared Vietnamese ingredient dataset")
    print(f"Output: {output_root.resolve()}")
    for split in split_keys:
        if copied_images[split]:
            print(f"{split}: {copied_images[split]} images")
    for item in INGREDIENT_CLASSES:
        print(f"{item.class_id:>2} {item.name_vi:<12}: {object_counts[item.class_id]} objects")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-yaml", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("ai_service/training/datasets/vietnamese_ingredients_v1"),
    )
    args = parser.parse_args()
    prepare(args.source_yaml.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
