import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';

abstract interface class NutritionTargetRepository {
  Future<bool> hasCurrentTarget();
  Future<void> createCalculatedTarget(DateTime effectiveFrom);
}

final class HttpNutritionTargetRepository implements NutritionTargetRepository {
  HttpNutritionTargetRepository(this._apiClient);

  final ApiClient _apiClient;
  static const _path = '/api/v1/me/nutrition-targets';

  @override
  Future<bool> hasCurrentTarget() async {
    try {
      final response = await _apiClient.requestJson(
        '$_path/current',
        authenticated: true,
      );
      if (response is! Map<String, dynamic> ||
          response['nutrientValues'] is! List) {
        throw const ApiResponseFormatException();
      }
      return (response['nutrientValues'] as List).isNotEmpty;
    } on ApiHttpException catch (error) {
      if (error.statusCode == 404 &&
          error.problem?.code == 'NO_CURRENT_TARGET') {
        return false;
      }
      rethrow;
    }
  }

  @override
  Future<void> createCalculatedTarget(DateTime effectiveFrom) async {
    final date =
        '${effectiveFrom.year.toString().padLeft(4, '0')}-'
        '${effectiveFrom.month.toString().padLeft(2, '0')}-'
        '${effectiveFrom.day.toString().padLeft(2, '0')}';
    await _apiClient.requestJson(
      '$_path/calculated',
      method: 'POST',
      authenticated: true,
      body: {'effectiveFrom': date},
    );
  }
}
