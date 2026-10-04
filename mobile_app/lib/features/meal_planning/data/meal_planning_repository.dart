import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';

abstract interface class MealPlanningRepository {
  Future<MealPlanGenerationResponse> generate(
    MealPlanGenerationRequest request,
  );

  /// Read or retry a persisted plan using the ID returned by [generate].
  Future<PersistedMealPlan> getPlan(String mealPlanPublicId);

  /// Read the ingredient shopping list for a persisted plan.
  Future<MealPlanShoppingList> getShoppingList(String mealPlanPublicId);
}

final class HttpMealPlanningRepository implements MealPlanningRepository {
  HttpMealPlanningRepository(this._apiClient, {this.csrfTokenProvider});

  static const _basePath = '/api/v1/me/meal-plans';

  final ApiClient _apiClient;
  final Future<CsrfToken> Function()? csrfTokenProvider;

  @override
  Future<MealPlanGenerationResponse> generate(
    MealPlanGenerationRequest request,
  ) async {
    final csrf = await csrfTokenProvider?.call();
    final response = await _apiClient.requestJson(
      '$_basePath/generate',
      method: 'POST',
      authenticated: true,
      headers: csrf == null ? const {} : {csrf.headerName: csrf.value},
      body: request.toJson(),
    );
    return MealPlanGenerationResponse.fromJson(response);
  }

  @override
  Future<PersistedMealPlan> getPlan(String mealPlanPublicId) async {
    final publicId = _planPathId(mealPlanPublicId);
    final response = await _apiClient.requestJson(
      '$_basePath/$publicId',
      authenticated: true,
    );
    return PersistedMealPlan.fromJson(response);
  }

  @override
  Future<MealPlanShoppingList> getShoppingList(String mealPlanPublicId) async {
    final publicId = _planPathId(mealPlanPublicId);
    final response = await _apiClient.requestJson(
      '$_basePath/$publicId/shopping-list',
      authenticated: true,
    );
    return MealPlanShoppingList.fromJson(response);
  }

  String _planPathId(String mealPlanPublicId) {
    final publicId = mealPlanPublicId.trim();
    if (publicId.isEmpty || publicId.contains('/')) {
      throw ArgumentError.value(mealPlanPublicId, 'mealPlanPublicId');
    }
    return Uri.encodeComponent(publicId);
  }
}
