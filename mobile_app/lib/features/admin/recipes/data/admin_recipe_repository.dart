import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/admin/recipes/data/admin_recipe_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';

abstract interface class AdminRecipeRepository {
  Future<AdminRecipePage> getRecipes({
    String? query,
    String? status,
    int page = 0,
    int size = 20,
  });

  Future<AdminRecipeDetail> getRecipe(String publicId);

  Future<AdminRecipeDetail> create(AdminRecipeUpsertRequest request);

  Future<AdminRecipeDetail> update(
    String publicId,
    AdminRecipeUpsertRequest request,
  );

  Future<AdminRecipeDetail> publish(String publicId);

  Future<AdminRecipeDetail> archive(String publicId);

  Future<List<RecipeTag>> getTags();

  Future<List<RecipeMealSlot>> getMealSlots();
}

final class HttpAdminRecipeRepository implements AdminRecipeRepository {
  HttpAdminRecipeRepository(this._apiClient);

  static const _path = '/api/v1/admin/recipes';
  final ApiClient _apiClient;

  @override
  Future<AdminRecipePage> getRecipes({
    String? query,
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    _validatePage(page, size);
    final parameters = <String, String>{'page': '$page', 'size': '$size'};
    final normalizedQuery = query?.trim() ?? '';
    final normalizedStatus = status?.trim() ?? '';
    if (normalizedQuery.isNotEmpty) parameters['q'] = normalizedQuery;
    if (normalizedStatus.isNotEmpty) parameters['status'] = normalizedStatus;
    final response = await _apiClient.requestJson(
      Uri(path: _path, queryParameters: parameters).toString(),
      authenticated: true,
    );
    return AdminRecipePage.fromJson(response);
  }

  @override
  Future<AdminRecipeDetail> getRecipe(String publicId) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      authenticated: true,
    );
    return AdminRecipeDetail.fromJson(response);
  }

  @override
  Future<AdminRecipeDetail> create(AdminRecipeUpsertRequest request) async {
    final response = await _apiClient.requestJson(
      _path,
      method: 'POST',
      body: request.toJson(),
      authenticated: true,
    );
    return AdminRecipeDetail.fromJson(response);
  }

  @override
  Future<AdminRecipeDetail> update(
    String publicId,
    AdminRecipeUpsertRequest request,
  ) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      method: 'PUT',
      body: request.toJson(),
      authenticated: true,
    );
    return AdminRecipeDetail.fromJson(response);
  }

  @override
  Future<AdminRecipeDetail> publish(String publicId) async => _lifecycle(
    publicId,
    'publish',
  );

  @override
  Future<AdminRecipeDetail> archive(String publicId) async => _lifecycle(
    publicId,
    'archive',
  );

  @override
  Future<List<RecipeTag>> getTags() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/recipe-tags',
      authenticated: true,
    );
    return CatalogJson.list(response, RecipeTag.fromJson);
  }

  @override
  Future<List<RecipeMealSlot>> getMealSlots() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/meal-slot-types',
      authenticated: true,
    );
    return CatalogJson.list(response, RecipeMealSlot.fromJson);
  }

  Future<AdminRecipeDetail> _lifecycle(
    String publicId,
    String action,
  ) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}/$action',
      method: 'POST',
      authenticated: true,
    );
    return AdminRecipeDetail.fromJson(response);
  }

  static String _requiredPublicId(String publicId) {
    final value = publicId.trim();
    if (value.isEmpty || value.contains('/')) {
      throw ArgumentError.value(publicId, 'publicId');
    }
    return value;
  }

  static void _validatePage(int page, int size) {
    if (page < 0) {
      throw ArgumentError.value(page, 'page', 'must be non-negative');
    }
    if (size < 1 || size > 100) {
      throw ArgumentError.value(size, 'size', 'must be between 1 and 100');
    }
  }
}
