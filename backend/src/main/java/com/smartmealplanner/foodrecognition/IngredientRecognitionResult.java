package com.smartmealplanner.foodrecognition;

import java.util.List;

/** Java-owned transport contract for YOLO ingredient recognition. */
public record IngredientRecognitionResult(
        String algorithmVersion,
        int imageWidth,
        int imageHeight,
        List<Detection> detections) {

    public IngredientRecognitionResult {
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion is required");
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        detections = detections == null ? List.of() : List.copyOf(detections);
    }

    public record Detection(
            int classId,
            String code,
            String nameVi,
            double confidence,
            BoundingBox box) {

        public Detection {
            if (classId < 0 || code == null || code.isBlank()
                    || nameVi == null || nameVi.isBlank()
                    || !Double.isFinite(confidence)
                    || confidence < 0.0 || confidence > 1.0
                    || box == null) {
                throw new IllegalArgumentException("Invalid ingredient detection");
            }
        }
    }

    public record BoundingBox(double x1, double y1, double x2, double y2) {
        public BoundingBox {
            if (!Double.isFinite(x1) || !Double.isFinite(y1)
                    || !Double.isFinite(x2) || !Double.isFinite(y2)
                    || x1 < 0.0 || y1 < 0.0 || x2 < x1 || y2 < y1) {
                throw new IllegalArgumentException("Invalid recognition bounding box");
            }
        }
    }
}
