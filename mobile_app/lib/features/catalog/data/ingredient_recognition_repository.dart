import 'dart:typed_data';

import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

final class IngredientRecognitionResult {
  const IngredientRecognitionResult({
    required this.algorithmVersion,
    required this.imageWidth,
    required this.imageHeight,
    required this.detections,
  });

  final String algorithmVersion;
  final int imageWidth;
  final int imageHeight;
  final List<IngredientRecognitionDetection> detections;

  factory IngredientRecognitionResult.fromJson(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const ApiResponseFormatException();
    }
    final algorithmVersion = value['algorithmVersion'];
    final imageWidth = value['imageWidth'];
    final imageHeight = value['imageHeight'];
    final rawDetections = value['detections'];
    if (algorithmVersion is! String ||
        algorithmVersion.isEmpty ||
        imageWidth is! int ||
        imageWidth <= 0 ||
        imageHeight is! int ||
        imageHeight <= 0 ||
        rawDetections is! List) {
      throw const ApiResponseFormatException();
    }
    return IngredientRecognitionResult(
      algorithmVersion: algorithmVersion,
      imageWidth: imageWidth,
      imageHeight: imageHeight,
      detections: rawDetections
          .map(IngredientRecognitionDetection.fromJson)
          .toList(growable: false),
    );
  }
}

final class IngredientRecognitionDetection {
  const IngredientRecognitionDetection({
    required this.classId,
    required this.code,
    required this.nameVi,
    required this.confidence,
    required this.box,
  });

  final int classId;
  final String code;
  final String nameVi;
  final double confidence;
  final RecognitionBoundingBox box;

  bool get needsConfirmation => confidence < 0.35;

  factory IngredientRecognitionDetection.fromJson(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const ApiResponseFormatException();
    }
    final classId = value['classId'];
    final code = value['code'];
    final nameVi = value['nameVi'];
    final confidence = value['confidence'];
    if (classId is! int ||
        classId < 0 ||
        code is! String ||
        code.isEmpty ||
        nameVi is! String ||
        nameVi.isEmpty ||
        confidence is! num ||
        confidence < 0 ||
        confidence > 1) {
      throw const ApiResponseFormatException();
    }
    return IngredientRecognitionDetection(
      classId: classId,
      code: code,
      nameVi: nameVi,
      confidence: confidence.toDouble(),
      box: RecognitionBoundingBox.fromJson(value['box']),
    );
  }
}

final class RecognitionBoundingBox {
  const RecognitionBoundingBox({
    required this.x1,
    required this.y1,
    required this.x2,
    required this.y2,
  });

  final double x1;
  final double y1;
  final double x2;
  final double y2;

  factory RecognitionBoundingBox.fromJson(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const ApiResponseFormatException();
    }
    final x1 = value['x1'];
    final y1 = value['y1'];
    final x2 = value['x2'];
    final y2 = value['y2'];
    if (x1 is! num ||
        y1 is! num ||
        x2 is! num ||
        y2 is! num ||
        x1 < 0 ||
        y1 < 0 ||
        x2 < x1 ||
        y2 < y1) {
      throw const ApiResponseFormatException();
    }
    return RecognitionBoundingBox(
      x1: x1.toDouble(),
      y1: y1.toDouble(),
      x2: x2.toDouble(),
      y2: y2.toDouble(),
    );
  }
}

abstract interface class IngredientRecognitionRepository {
  Future<IngredientRecognitionResult> detect({
    required Uint8List imageBytes,
    required String contentType,
  });
}

final class HttpIngredientRecognitionRepository
    implements IngredientRecognitionRepository {
  HttpIngredientRecognitionRepository(
    this._apiClient, {
    this.csrfTokenProvider,
  });

  static const _path = '/api/v1/food-recognition/ingredients:detect';

  final ApiClient _apiClient;
  final Future<CsrfToken> Function()? csrfTokenProvider;

  @override
  Future<IngredientRecognitionResult> detect({
    required Uint8List imageBytes,
    required String contentType,
  }) async {
    final csrf = await csrfTokenProvider?.call();
    final response = await _apiClient.requestBytesJson(
      _path,
      bytes: imageBytes,
      contentType: contentType,
      authenticated: true,
      requestTimeout: const Duration(seconds: 45),
      headers: csrf == null ? const {} : {csrf.headerName: csrf.value},
    );
    return IngredientRecognitionResult.fromJson(response);
  }
}
