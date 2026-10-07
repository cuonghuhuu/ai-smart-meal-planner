"""Evaluate a trained Vietnamese ingredient detector and persist headline metrics."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--data",
        type=Path,
        default=Path("ai_service/training/datasets/vietnamese_ingredients_v1/data.yaml"),
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=Path(
            "ai_service/training/runs/ingredients_yolo11n_v1/weights/best.pt"
        ),
    )
    parser.add_argument("--split", choices=("val", "test"), default="test")
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--batch", type=int, default=4)
    parser.add_argument("--device", default="0")
    args = parser.parse_args()

    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise SystemExit(
            'YOLO dependencies are missing. Install with: pip install -e "./ai_service[vision]"'
        ) from exc

    if not args.data.exists():
        raise SystemExit(f"Dataset configuration does not exist: {args.data}")
    if not args.model.exists():
        raise SystemExit(f"Trained checkpoint does not exist: {args.model}")

    model = YOLO(str(args.model))
    metrics = model.val(
        data=str(args.data),
        split=args.split,
        imgsz=args.imgsz,
        batch=args.batch,
        device=args.device,
        plots=True,
        project="ai_service/training/runs",
        name=f"ingredients_yolo11n_v1_{args.split}_eval",
        exist_ok=True,
    )

    summary = {
        "split": args.split,
        "precision": float(metrics.box.mp),
        "recall": float(metrics.box.mr),
        "map50": float(metrics.box.map50),
        "map50_95": float(metrics.box.map),
    }

    save_dir = Path(metrics.save_dir)
    save_dir.mkdir(parents=True, exist_ok=True)
    metrics_path = save_dir / "metrics_summary.json"
    metrics_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"Saved metrics summary: {metrics_path}")


if __name__ == "__main__":
    main()
