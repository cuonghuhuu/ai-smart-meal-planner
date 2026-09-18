import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';

abstract interface class PreferencesRepository {
  Future<List<DietaryPreferenceReference>> getDietaryPreferenceReferences();

  Future<List<SelectedDietaryPreference>> getSelectedDietaryPreferences();

  Future<List<SelectedDietaryPreference>> replaceDietaryPreferences(
    List<String> codes,
  );

  Future<List<AllergenReference>> getAllergenReferences();

  Future<List<SelectedAllergen>> getSelectedAllergens();

  Future<List<SelectedAllergen>> replaceAllergens(
    List<AllergenSelection> selections,
  );
}

final class HttpPreferencesRepository implements PreferencesRepository {
  HttpPreferencesRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<List<DietaryPreferenceReference>>
  getDietaryPreferenceReferences() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/dietary-preferences',
      authenticated: true,
    );
    return _list(response, DietaryPreferenceReference.fromJson);
  }

  @override
  Future<List<SelectedDietaryPreference>>
  getSelectedDietaryPreferences() async {
    final response = await _apiClient.requestJson(
      '/api/v1/me/dietary-preferences',
      authenticated: true,
    );
    return _list(response, SelectedDietaryPreference.fromJson);
  }

  @override
  Future<List<SelectedDietaryPreference>> replaceDietaryPreferences(
    List<String> codes,
  ) async {
    final response = await _apiClient.requestJson(
      '/api/v1/me/dietary-preferences',
      method: 'PUT',
      body: {'preferences': codes},
      authenticated: true,
    );
    return _list(response, SelectedDietaryPreference.fromJson);
  }

  @override
  Future<List<AllergenReference>> getAllergenReferences() async {
    final response = await _apiClient.requestJson(
      '/api/v1/reference/allergens',
      authenticated: true,
    );
    return _list(response, AllergenReference.fromJson);
  }

  @override
  Future<List<SelectedAllergen>> getSelectedAllergens() async {
    final response = await _apiClient.requestJson(
      '/api/v1/me/allergens',
      authenticated: true,
    );
    return _list(response, SelectedAllergen.fromJson);
  }

  @override
  Future<List<SelectedAllergen>> replaceAllergens(
    List<AllergenSelection> selections,
  ) async {
    final response = await _apiClient.requestJson(
      '/api/v1/me/allergens',
      method: 'PUT',
      body: {
        'allergens': [for (final selection in selections) selection.toJson()],
      },
      authenticated: true,
    );
    return _list(response, SelectedAllergen.fromJson);
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
