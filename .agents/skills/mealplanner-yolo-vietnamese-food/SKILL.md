---
name: mealplanner-yolo-vietnamese-food
description: Build, train, evaluate, and integrate the Vietnamese ingredient/dish YOLO pipeline for AI Smart Meal Planner. Use for dataset preparation, leakage-safe splits, transfer learning, metrics, checkpoints, and FastAPI inference.
---
# Meal Planner YOLO Pipeline

## Academic contract

- Describe pretrained initialization as transfer learning/fine-tuning, not training from scratch.
- Preserve dataset source/license/provenance.
- YOLO returns visual class, confidence, and bounding box. Nutrition comes from Java-owned catalog data after mapping.
- Keep finished-dish detection and raw-ingredient detection as separate model boundaries unless an experiment justifies merging them.

## Ingredient V1

The supported user-facing classes are:

0 Trứng gà
1 Cà chua
2 Cà rốt
3 Hành tây
4 Tỏi
5 Dưa chuột
6 Khoai tây
7 Thịt gà
8 Thịt lợn
9 Thịt bò
10 Cá
11 Bắp cải

Stable machine codes live in app.food_recognition.catalog.

## Workflow

1. Validate image/label parity and source data.yaml.
2. Detect augmentation families and split by original-source group, never by augmented file independently.
3. Run split_roboflow_dataset.py.
4. Run prepare_ingredient_dataset.py to filter/remap classes and emit Vietnamese data.yaml.
5. Inspect per-class counts before training.
6. Smoke-train 1-2 epochs on CUDA.
7. Fine-tune YOLO11n on the RTX 2050 4 GiB; reduce batch before reducing image size if CUDA OOM occurs.
8. Evaluate on the held-out test split and save Precision, Recall, mAP50, mAP50-95, confusion matrix, and curves.
9. Copy only the selected best.pt into the runtime model path; keep datasets/runs/checkpoints out of Git.
10. Verify FastAPI inference with a real unseen image.

## Quality rules

- No data leakage between train/val/test.
- No fabricated metrics.
- No claim that an external Vietnamese-dish checkpoint was trained by the team.
- Keep experiment evidence sufficient for course defense.
