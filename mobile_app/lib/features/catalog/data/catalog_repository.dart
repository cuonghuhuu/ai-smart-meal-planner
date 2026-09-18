import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

abstract interface class CatalogRepository {
  Future<FoodCatalogPage> getFoods({
    String? query,
    String? categoryCode,
    int page = 0,
    int size = 20,
  });

  Future<FoodCatalogDetail> getFood(String publicId);

  Future<List<CatalogCategory>> getFoodCategories();

  Future<IngredientCatalogPage> getIngredients({
    String? query,
    String? categoryCode,
    int page = 0,
    int size = 20,
  });

  Future<IngredientCatalogDetail> getIngredient(String publicId);
}

final class HttpCatalogRepository implements CatalogRepository {
  HttpCatalogRepository(this._apiClient);

  static const _foodPath = '/api/v1/foods';
  static const _ingredientPath = '/api/v1/ingredients';

  final ApiClient _apiClient;

  @override
  Future<FoodCatalogPage> getFoods({
    String? query,
    String? categoryCode,
    int page = 0,
    int size = 20,
  }) async {
    _validatePage(page, size);
    final response = await _apiClient.requestJson(
      _pagePath(
        _foodPath,
        query: query,
        categoryCode: categoryCode,
        page: page,
        size: size,
      ),
      authenticated: true,
    );
    return FoodCatalogPage.fromJson(response);
  }

  @override
  Future<FoodCatalogDetail> getFood(String publicId) async {
    final response = await _apiClient.requestJson(
      '$_foodPath/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      authenticated: true,
    );
    return FoodCatalogDetail.fromJson(response);
  }

  @override
  Future<List<CatalogCategory>> getFoodCategories() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/food-categories',
      authenticated: true,
    );
    return CatalogJson.list(response, CatalogCategory.fromJson);
  }

  @override
  Future<IngredientCatalogPage> getIngredients({
    String? query,
    String? categoryCode,
    int page = 0,
    int size = 20,
  }) async {
    _validatePage(page, size);
    final response = await _apiClient.requestJson(
      _pagePath(
        _ingredientPath,
        query: query,
        categoryCode: categoryCode,
        page: page,
        size: size,
      ),
      authenticated: true,
    );
    return IngredientCatalogPage.fromJson(response);
  }

  @override
  Future<IngredientCatalogDetail> getIngredient(String publicId) async {
    final response = await _apiClient.requestJson(
      '$_ingredientPath/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      authenticated: true,
    );
    return IngredientCatalogDetail.fromJson(response);
  }

  String _pagePath(
    String basePath, {
    String? query,
    String? categoryCode,
    required int page,
    required int size,
  }) {
    final queryParameters = <String, String>{
      'page': '$page',
      'size': '$size',
    };
    final trimmedQuery = query?.trim() ?? '';
    final trimmedCategory = categoryCode?.trim() ?? '';
    if (trimmedQuery.isNotEmpty) {
      queryParameters['q'] = trimmedQuery;
    }
    if (trimmedCategory.isNotEmpty) {
      queryParameters['categoryCode'] = trimmedCategory;
    }
    return Uri(path: basePath, queryParameters: queryParameters).toString();
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
