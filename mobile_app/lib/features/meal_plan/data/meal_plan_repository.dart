import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/meal_plan/data/meal_plan_models.dart';

abstract interface class MealPlanRepository {
  Future<MealPlanGeneration> generate(MealPlanGenerationRequest request);

  Future<List<MealPlanItem>> getMealPlans();

  Future<MealPlanDetail> getMealPlan(String publicId);

  Future<MealPlanDetail> acceptMealPlan(String publicId);
}

final class HttpMealPlanRepository implements MealPlanRepository {
  HttpMealPlanRepository(this._apiClient);

  static const _path = '/api/v1/me/meal-plans';
  final ApiClient _apiClient;

  @override
  Future<MealPlanGeneration> generate(MealPlanGenerationRequest request) async {
    final response = await _apiClient.requestJson(
      '$_path/generate',
      method: 'POST',
      body: request.toJson(),
      authenticated: true,
    );
    return MealPlanGeneration.fromJson(response);
  }

  @override
  Future<List<MealPlanItem>> getMealPlans() async {
    final response = await _apiClient.requestJson(
      _path,
      authenticated: true,
    );
    return CatalogJson.list(response, MealPlanItem.fromJson);
  }

  @override
  Future<MealPlanDetail> getMealPlan(String publicId) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      authenticated: true,
    );
    return MealPlanDetail.fromJson(response);
  }

  @override
  Future<MealPlanDetail> acceptMealPlan(String publicId) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}/accept',
      method: 'POST',
      authenticated: true,
    );
    return MealPlanDetail.fromJson(response);
  }

  static String _requiredPublicId(String publicId) {
    final value = publicId.trim();
    if (value.isEmpty || value.contains('/')) {
      throw ArgumentError.value(publicId, 'publicId');
    }
    return value;
  }
}
