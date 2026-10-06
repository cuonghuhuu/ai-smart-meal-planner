"""Local Ultralytics detector with strict model/catalog validation."""

from __future__ import annotations

import os
from functools import cached_property
from pathlib import Path
from typing import Any

from app.food_recognition.catalog import INGREDIENT_CLASS_BY_ID, vietnamese_names
from app.food_recognition.contracts import (
    BoundingBox,
    IngredientDetection,
    IngredientRecognitionResponse,
)


class RecognitionConfigurationError(RuntimeError):
    """Raised when the local recognition runtime is not correctly configured."""


class InvalidRecognitionImage(ValueError):
    """Raised when uploaded bytes cannot be decoded into a supported image."""


class YoloIngredientDetector:
    """Run the locally stored V1 ingredient checkpoint on one image at a time."""

    def __init__(self, model_path: Path, confidence: float = 0.35, device: str = "0") -> None:
        if not 0 < confidence <= 1:
            raise ValueError("Recognition confidence must be within (0, 1]")
        self.model_path = model_path
        self.confidence = confidence
        self.device = device

    @classmethod
    def from_environment(cls) -> "YoloIngredientDetector":
        default_path = Path(
            "ai_service/app/food_recognition/models/ingredients/best.pt"
        )
        return cls(
            Path(os.getenv("AI_INGREDIENT_MODEL_PATH", str(default_path))),
            float(os.getenv("AI_RECOGNITION_CONFIDENCE", "0.35")),
            os.getenv("AI_VISION_DEVICE", "0"),
        )

    @cached_property
    def model(self) -> Any:
        if not self.model_path.exists():
            raise RecognitionConfigurationError(
                f"Ingredient model does not exist: {self.model_path}"
            )
        try:
            from ultralytics import YOLO
        except ImportError as exc:
            raise RecognitionConfigurationError(
                'Vision dependencies are missing; install "./ai_service[vision]"'
            ) from exc

        model = YOLO(str(self.model_path))
        raw_names = model.names
        if isinstance(raw_names, list):
            names = {index: str(name) for index, name in enumerate(raw_names)}
        else:
            names = {int(index): str(name) for index, name in raw_names.items()}
        expected = vietnamese_names()
        if names != expected:
            raise RecognitionConfigurationError(
                f"Model class catalog mismatch. Expected {expected}, got {names}"
            )
        return model

    def detect(self, image_bytes: bytes) -> IngredientRecognitionResponse:
        try:
            import cv2
            import numpy as np
        except ImportError as exc:
            raise RecognitionConfigurationError(
                'Vision dependencies are missing; install "./ai_service[vision]"'
            ) from exc

        encoded = np.frombuffer(image_bytes, dtype=np.uint8)
        image = cv2.imdecode(encoded, cv2.IMREAD_COLOR)
        if image is None:
            raise InvalidRecognitionImage("Uploaded bytes are not a decodable image")

        height, width = image.shape[:2]
        if width * height > 25_000_000:
            raise InvalidRecognitionImage("Image exceeds the 25 megapixel safety limit")

        results = self.model.predict(
            source=image,
            conf=self.confidence,
            device=self.device,
            verbose=False,
        )
        result = results[0]
        detections: list[IngredientDetection] = []
        boxes = result.boxes
        if boxes is not None:
            class_ids = boxes.cls.detach().cpu().tolist()
            confidences = boxes.conf.detach().cpu().tolist()
            coordinates = boxes.xyxy.detach().cpu().tolist()
            for raw_class_id, confidence, xyxy in zip(
                class_ids, confidences, coordinates, strict=True
            ):
                class_id = int(raw_class_id)
                catalog_item = INGREDIENT_CLASS_BY_ID.get(class_id)
                if catalog_item is None:
                    raise RecognitionConfigurationError(
                        f"Model produced unsupported class id {class_id}"
                    )
                x1, y1, x2, y2 = (float(value) for value in xyxy)
                detections.append(
                    IngredientDetection(
                        class_id=class_id,
                        code=catalog_item.code,
                        name_vi=catalog_item.name_vi,
                        confidence=float(confidence),
                        box=BoundingBox(x1=x1, y1=y1, x2=x2, y2=y2),
                    )
                )

        detections.sort(key=lambda item: item.confidence, reverse=True)
        return IngredientRecognitionResponse(
            image_width=width,
            image_height=height,
            detections=detections,
        )
