# YOLO Vietnamese Food Recognition — Rescue V1

## Goal

The rescue build provides real local computer-vision inference while keeping training provenance academically defensible. YOLO recognizes visual classes; it does not infer calories directly. Java later maps a recognized class to the authoritative Ingredient or Recipe catalog and nutrition data.

## Ingredient detector V1

The first trainable detector uses twelve classes. User-facing names are Vietnamese while stable ASCII codes are retained internally for API/database mapping.

| ID | Code | Vietnamese name |
| ---: | --- | --- |
| 0 | TRUNG_GA | Trứng gà |
| 1 | CA_CHUA | Cà chua |
| 2 | CA_ROT | Cà rốt |
| 3 | HANH_TAY | Hành tây |
| 4 | TOI | Tỏi |
| 5 | DUA_CHUOT | Dưa chuột |
| 6 | KHOAI_TAY | Khoai tây |
| 7 | THIT_GA | Thịt gà |
| 8 | THIT_LON | Thịt lợn |
| 9 | THIT_BO | Thịt bò |
| 10 | CA | Cá |
| 11 | BAP_CAI | Bắp cải |

The detector is fine-tuned from pretrained YOLO11n weights. This is transfer learning, not training from random initialization.

## Dataset rule

Do not rename a source data.yaml and assume numeric labels changed with it. The preparation script reads the original class ids, keeps only the twelve supported classes, remaps them to dense ids 0..11, validates normalized YOLO boxes and writes a new UTF-8 data.yaml with Vietnamese class names.

A candidate source dataset must actually contain all twelve classes. The preparation step fails closed if any promised class is absent.

Verified public candidates as of 2026-10-06:

- Roboflow Universe temp_project_7: about 1.6k images, 20 classes, CC BY 4.0. Its published class list includes all twelve rescue classes. This is the fast overnight option.
- Roboflow Universe food ingredients (my-workspace-idgwe): about 40.4k images, 20 classes, CC BY 4.0. Its published class list also includes all twelve rescue classes. This is the larger follow-up dataset.

Record the exact dataset/version URL and license used for the final trained checkpoint in the experiment report.

## Preparation

After exporting a source dataset in Ultralytics YOLO format:

    python ai_service/training/prepare_ingredient_dataset.py --source-yaml <source-folder>/data.yaml

Expected output is under ai_service/training/datasets/vietnamese_ingredients_v1 with data.yaml plus images/ and labels/ train/val/test splits.

## Training on the rescue laptop

The verified local machine has an RTX 2050 with 4 GiB VRAM. Start with YOLO11n, 640 px input and batch 4:

    python ai_service/training/train_ingredients.py

If CUDA reports out-of-memory, reduce batch to 2 first. If memory is still insufficient, use 512 px with batch 2. Do not silently fall back to CPU for the overnight rescue run.

Ultralytics writes training arguments, plots and weights under ai_service/training/runs/ingredients_yolo11n_v1/. Keep these artifacts as experiment evidence.

## Dish detector

Dish recognition is a separate model boundary because finished dishes and raw ingredients have different visual distributions. VietFood67 is the preferred Vietnamese-dish reference dataset/checkpoint: its authors report 33,003 images across 68 classes and a YOLOv10 model with mAP50 around 0.92. The project states that VietFood67 is free for research/educational use with citation and prohibits commercial use or redistribution.

If the rescue build integrates a pretrained VietFood67 checkpoint, describe it as an externally trained checkpoint. Do not claim that the team trained it.

## Runtime direction

    image -> YOLO detector -> class id + Vietnamese name + confidence + bounding box
          -> Java catalog mapping -> Ingredient / Recipe -> nutrition / pantry / recommendation

This keeps computer vision separate from authoritative business and nutrition state.
