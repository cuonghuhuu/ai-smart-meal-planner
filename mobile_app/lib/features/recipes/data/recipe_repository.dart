import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';

abstract interface class RecipeRepository {
  Future<RecipePage> getRecipes({
    String? query,
    int page = 0,
    int size = 20,
  });

  Future<RecipeDetail> getRecipe(String publicId);
}

final class HttpRecipeRepository implements RecipeRepository {
  HttpRecipeRepository(this._apiClient);

  static const _path = '/api/v1/recipes';
  final ApiClient _apiClient;

  @override
  Future<RecipePage> getRecipes({
    String? query,
    int page = 0,
    int size = 20,
  }) async {
    if (page < 0 || size < 1 || size > 100) {
      throw ArgumentError('Invalid recipe page or size');
    }
    final parameters = <String, String>{
      'page': '$page',
      'size': '$size',
    };
    final normalizedQuery = query?.trim() ?? '';
    if (normalizedQuery.isNotEmpty) {
      parameters['q'] = normalizedQuery;
    }
    final response = await _apiClient.requestJson(
      Uri(path: _path, queryParameters: parameters).toString(),
      authenticated: true,
    );
    return RecipePage.fromJson(response);
  }

  @override
  Future<RecipeDetail> getRecipe(String publicId) async {
    final value = publicId.trim();
    if (value.isEmpty || value.contains('/')) {
      throw ArgumentError.value(publicId, 'publicId');
    }
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(value)}',
      authenticated: true,
    );
    return RecipeDetail.fromJson(response);
  }
}
