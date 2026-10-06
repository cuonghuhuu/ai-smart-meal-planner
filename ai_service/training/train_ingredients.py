"""Fine-tune YOLO11n for the Vietnamese ingredient detector."""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--data",
        type=Path,
        default=Path("ai_service/training/datasets/vietnamese_ingredients_v1/data.yaml"),
    )
    parser.add_argument("--model", default="yolo11n.pt")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--batch", type=int, default=4)
    parser.add_argument("--device", default="0")
    parser.add_argument("--workers", type=int, default=2)
    args = parser.parse_args()

    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise SystemExit(
            'YOLO dependencies are missing. Install with: pip install -e "./ai_service[vision]"'
        ) from exc

    if not args.data.exists():
        raise SystemExit(f"Dataset configuration does not exist: {args.data}")

    model = YOLO(args.model)
    model.train(
        data=str(args.data),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        device=args.device,
        workers=args.workers,
        patience=10,
        seed=42,
        deterministic=True,
        amp=True,
        cache=False,
        project="ai_service/training/runs",
        name="ingredients_yolo11n_v1",
        exist_ok=False,
        plots=True,
    )


if __name__ == "__main__":
    main()
