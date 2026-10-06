import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';

import 'support/fake_catalog_repository.dart';

void main() {
  test('ingredient controller exposes YOLO recognition result', () async {
    final recognition = _FakeRecognitionRepository(
      const IngredientRecognitionResult(
        algorithmVersion: 'YOLO11N_INGREDIENT_V1',
        imageWidth: 595,
        imageHeight: 336,
        detections: [
          IngredientRecognitionDetection(
            classId: 8,
            code: 'THIT_LON',
            nameVi: 'Thịt lợn',
            confidence: 0.82,
            box: RecognitionBoundingBox(
              x1: 1,
              y1: 2,
              x2: 30,
              y2: 40,
            ),
          ),
        ],
      ),
    );
    final controller = IngredientCatalogController(
      repository: FakeCatalogRepository(),
      recognitionRepository: recognition,
    );
    addTearDown(controller.dispose);

    await controller.recognizeImage(
      Uint8List.fromList([1, 2, 3]),
      'image/jpeg',
    );

    expect(
      controller.recognitionState.status,
      IngredientRecognitionStatus.loaded,
    );
    expect(
      controller.recognitionState.result!.detections.single.nameVi,
      'Thịt lợn',
    );
    expect(recognition.calls, 1);
  });
}

final class _FakeRecognitionRepository
    implements IngredientRecognitionRepository {
  _FakeRecognitionRepository(this.result);

  final IngredientRecognitionResult result;
  int calls = 0;

  @override
  Future<IngredientRecognitionResult> detect({
    required Uint8List imageBytes,
    required String contentType,
  }) async {
    calls++;
    expect(imageBytes, [1, 2, 3]);
    expect(contentType, 'image/jpeg');
    return result;
  }
}
