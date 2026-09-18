import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';

abstract interface class ReferenceDataRepository {
  Future<List<ActivityLevelReference>> getActivityLevels();

  Future<List<NutritionGoalReference>> getNutritionGoals();
}

final class HttpReferenceDataRepository implements ReferenceDataRepository {
  HttpReferenceDataRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<List<ActivityLevelReference>> getActivityLevels() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/activity-levels',
      authenticated: true,
    );
    return _list(response, ActivityLevelReference.fromJson);
  }

  @override
  Future<List<NutritionGoalReference>> getNutritionGoals() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/nutrition-goals',
      authenticated: true,
    );
    return _list(response, NutritionGoalReference.fromJson);
  }
}

List<T> _list<T>(Object? response, T Function(Map<String, dynamic>) parse) {
  if (response is! List) {
    throw const ApiResponseFormatException();
  }
  return response
      .map((item) {
        if (item is! Map<String, dynamic>) {
          throw const ApiResponseFormatException();
        }
        return parse(item);
      })
      .toList(growable: false);
}
