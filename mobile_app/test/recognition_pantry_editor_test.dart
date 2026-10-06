import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';
import 'package:smart_meal_planner/features/catalog/presentation/recognition_pantry_editor.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';

import 'support/fake_catalog_repository.dart';
import 'support/fake_pantry_repository.dart';

void main() {
  testWidgets('manual catalog correction creates a real pantry request', (
    tester,
  ) async {
    final catalog = FakeCatalogRepository();
    final pantry = FakePantryRepository();
    final controller = PantryController(repository: pantry);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: RecognitionPantryEditor(
              result: const IngredientRecognitionResult(
                algorithmVersion: 'YOLO11N_INGREDIENT_V1',
                imageWidth: 100,
                imageHeight: 100,
                detections: [],
              ),
              catalogRepository: catalog,
              pantryController: controller,
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('recognition-add-manual')));
    await tester.pump();
    await tester.tap(find.text('Tìm trong danh mục'));
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('pantry-pick-$testIngredientId')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tôi xác nhận nguyên liệu này'));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('recognition-add-to-pantry')));
    await tester.pumpAndSettle();

    expect(pantry.createRequests, hasLength(1));
    expect(pantry.createRequests.single.ingredientPublicId, testIngredientId);
    expect(pantry.createRequests.single.quantity, '100');
    expect(pantry.createRequests.single.unitCode, 'g');
    expect(find.text('Đã thêm 1 nguyên liệu vào kho.'), findsOneWidget);
  });

  testWidgets('unconfirmed detection never writes to pantry', (tester) async {
    final pantry = FakePantryRepository();
    final controller = PantryController(repository: pantry);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: RecognitionPantryEditor(
              result: const IngredientRecognitionResult(
                algorithmVersion: 'YOLO11N_INGREDIENT_V1',
                imageWidth: 100,
                imageHeight: 100,
                detections: [
                  IngredientRecognitionDetection(
                    classId: 1,
                    code: 'CA_CHUA',
                    nameVi: 'Cà chua',
                    confidence: 0.8,
                    box: RecognitionBoundingBox(x1: 1, y1: 1, x2: 10, y2: 10),
                  ),
                ],
              ),
              catalogRepository: FakeCatalogRepository(),
              pantryController: controller,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('recognition-add-to-pantry')));
    await tester.pump();
    expect(pantry.createRequests, isEmpty);
    expect(find.text('Hãy xác nhận ít nhất một nguyên liệu.'), findsOneWidget);
  });
}
