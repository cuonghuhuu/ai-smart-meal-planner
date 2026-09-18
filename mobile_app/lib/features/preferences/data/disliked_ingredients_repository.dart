import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';

abstract interface class DislikedIngredientsRepository {
  Future<List<DislikedIngredientPreference>> getDislikedIngredients();

  Future<List<DislikedIngredientPreference>> replaceDislikedIngredients(
    List<DislikedIngredientSelection> selections,
  );
}

final class HttpDislikedIngredientsRepository
    implements DislikedIngredientsRepository {
  HttpDislikedIngredientsRepository(this._apiClient);

  static const _path = '/api/v1/me/disliked-ingredients';

  final ApiClient _apiClient;

  @override
  Future<List<DislikedIngredientPreference>> getDislikedIngredients() async {
    final response = await _apiClient.requestJson(
      _path,
      authenticated: true,
    );
    return _list(response, DislikedIngredientPreference.fromJson);
  }

  @override
  Future<List<DislikedIngredientPreference>> replaceDislikedIngredients(
    List<DislikedIngredientSelection> selections,
  ) async {
    final response = await _apiClient.requestJson(
      _path,
      method: 'PUT',
      body: {
        'ingredients': [
          for (final selection in selections) selection.toJson(),
        ],
      },
      authenticated: true,
    );
    return _list(response, DislikedIngredientPreference.fromJson);
  }
}

List<T> _list<T>(Object? response, T Function(Object? value) parse) {
  if (response is! List) {
    throw const ApiResponseFormatException();
  }
  return response.map<T>((item) => parse(item)).toList(growable: false);
}
