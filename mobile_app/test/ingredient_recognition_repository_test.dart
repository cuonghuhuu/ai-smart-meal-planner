import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

void main() {
  test('recognition repository sends image bytes and parses Vietnamese result',
      () async {
    final image = Uint8List.fromList([1, 2, 3, 4]);
    final client = ApiClient(
      baseUrl: 'http://localhost:8080',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(
          request.url.path,
          '/api/v1/food-recognition/ingredients:detect',
        );
        expect(request.headers['content-type'], 'image/jpeg');
        expect(request.headers['authorization'], 'Bearer access-token');
        expect(request.headers['x-csrf-token'], 'csrf-token');
        expect(request.bodyBytes, image);

        final body = utf8.encode(
          '{"algorithmVersion":"YOLO11N_INGREDIENT_V1",'
          '"imageWidth":595,"imageHeight":336,'
          '"detections":[{"classId":8,"code":"THIT_LON",'
          '"nameVi":"Thịt lợn","confidence":0.82,'
          '"box":{"x1":1.0,"y1":2.0,"x2":30.0,"y2":40.0}}]}',
        );
        return http.Response.bytes(
          body,
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        );
      }),
    );
    client.configureAuthentication(
      accessTokenProvider: () => 'access-token',
      refreshAccessToken: () async => false,
    );

    final repository = HttpIngredientRecognitionRepository(
      client,
      csrfTokenProvider: () async =>
          const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf-token'),
    );
    final result = await repository.detect(
      imageBytes: image,
      contentType: 'image/jpeg',
    );

    expect(result.algorithmVersion, 'YOLO11N_INGREDIENT_V1');
    expect(result.detections, hasLength(1));
    expect(result.detections.single.nameVi, 'Thịt lợn');
    expect(result.detections.single.code, 'THIT_LON');
    expect(result.detections.single.needsConfirmation, isFalse);
  });

  test('low confidence recognition is marked for user confirmation', () {
    final result = IngredientRecognitionResult.fromJson({
      'algorithmVersion': 'YOLO11N_INGREDIENT_V1',
      'imageWidth': 100,
      'imageHeight': 100,
      'detections': [
        {
          'classId': 5,
          'code': 'DUA_CHUOT',
          'nameVi': 'Dưa chuột',
          'confidence': 0.166,
          'box': {'x1': 1, 'y1': 2, 'x2': 30, 'y2': 40},
        },
      ],
    });

    expect(result.detections.single.needsConfirmation, isTrue);
  });
}
