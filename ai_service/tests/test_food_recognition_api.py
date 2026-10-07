from fastapi.testclient import TestClient

from app.food_recognition.api import get_ingredient_detector
from app.food_recognition.contracts import (
    BoundingBox,
    IngredientDetection,
    IngredientRecognitionResponse,
)
from app.main import create_app
from app.settings import InternalServiceSettings

TOKEN = "r" * 32


class FakeDetector:
    def detect(self, image_bytes: bytes) -> IngredientRecognitionResponse:
        assert image_bytes == b"fake-image"
        return IngredientRecognitionResponse(
            image_width=640,
            image_height=480,
            detections=[
                IngredientDetection(
                    class_id=1,
                    code="CA_CHUA",
                    name_vi="Cà chua",
                    confidence=0.93,
                    box=BoundingBox(x1=10, y1=20, x2=100, y2=120),
                )
            ],
        )


def test_ingredient_recognition_endpoint_returns_vietnamese_contract() -> None:
    app = create_app(InternalServiceSettings(service_token=TOKEN))
    app.dependency_overrides[get_ingredient_detector] = lambda: FakeDetector()
    client = TestClient(app)

    response = client.post(
        "/internal/v1/food-recognition/ingredients:detect",
        content=b"fake-image",
        headers={
            "X-Internal-Service-Token": TOKEN,
            "Content-Type": "application/octet-stream",
        },
    )

    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json; charset=utf-8"
    assert "Cà chua" in response.content.decode("utf-8")
    assert response.json() == {
        "algorithmVersion": "YOLO11N_INGREDIENT_V1",
        "imageWidth": 640,
        "imageHeight": 480,
        "detections": [
            {
                "classId": 1,
                "code": "CA_CHUA",
                "nameVi": "Cà chua",
                "confidence": 0.93,
                "box": {"x1": 10.0, "y1": 20.0, "x2": 100.0, "y2": 120.0},
            }
        ],
    }
