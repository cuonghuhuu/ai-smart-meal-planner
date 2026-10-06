import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_json.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';

abstract interface class RecipeRepository {
  Future<RecipePage> getRecipes({
    String? query,
    String? mealSlotCode,
    String? tagCode,
    int? maxMinutes,
    int page = 0,
    int size = 20,
  });

  Future<RecipeDetail> getRecipe(String publicId);
  Future<List<RecipeTag>> getRecipeTags();
  Future<List<RecipeMealSlot>> getMealSlotTypes();
}

final class HttpRecipeRepository implements RecipeRepository {
  HttpRecipeRepository(this._apiClient);

  static const _basePath = '/api/v1/recipes';
  final ApiClient _apiClient;

  @override
  Future<RecipePage> getRecipes({
    String? query,
    String? mealSlotCode,
    String? tagCode,
    int? maxMinutes,
    int page = 0,
    int size = 20,
  }) async {
    if (page < 0) {
      throw ArgumentError.value(page, 'page', 'must be non-negative');
    }
    if (size < 1 || size > 100) {
      throw ArgumentError.value(size, 'size', 'must be between 1 and 100');
    }
    if (maxMinutes != null && (maxMinutes < 0 || maxMinutes > 10080)) {
      throw ArgumentError.value(
        maxMinutes,
        'maxMinutes',
        'must be between 0 and 10080',
      );
    }
    final normalizedQuery = query?.trim();
    if (normalizedQuery != null && normalizedQuery.length > 200) {
      throw ArgumentError.value(query, 'query', 'maximum length is 200');
    }
    final parameters = <String, String>{'page': '$page', 'size': '$size'};
    if (normalizedQuery != null && normalizedQuery.isNotEmpty) {
      parameters['q'] = normalizedQuery;
    }
    final slot = mealSlotCode?.trim();
    if (slot != null && slot.isNotEmpty) parameters['mealSlotCode'] = slot;
    final tag = tagCode?.trim();
    if (tag != null && tag.isNotEmpty) parameters['tagCode'] = tag;
    if (maxMinutes != null) parameters['maxMinutes'] = '$maxMinutes';

    final response = await _apiClient.requestJson(
      Uri(path: _basePath, queryParameters: parameters).toString(),
      authenticated: true,
    );
    return RecipePage.fromJson(response);
  }

  @override
  Future<RecipeDetail> getRecipe(String publicId) async {
    final normalizedId = publicId.trim();
    if (normalizedId.isEmpty || normalizedId.contains('/')) {
      throw ArgumentError.value(publicId, 'publicId');
    }
    final response = await _apiClient.requestJson(
      '$_basePath/${Uri.encodeComponent(normalizedId)}',
      authenticated: true,
    );
    return RecipeDetail.fromJson(response);
  }

  @override
  Future<List<RecipeTag>> getRecipeTags() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/recipe-tags',
      authenticated: true,
    );
    return RecipeJson.list(response, RecipeTag.fromJson);
  }

  @override
  Future<List<RecipeMealSlot>> getMealSlotTypes() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/meal-slot-types',
      authenticated: true,
    );
    return RecipeJson.list(response, RecipeMealSlot.fromJson);
  }
}
