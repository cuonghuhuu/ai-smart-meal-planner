import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_prerequisites.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_controller.dart';
import 'package:smart_meal_planner/features/meal_planning/data/nutrition_target_repository.dart';
import 'package:smart_meal_planner/features/measurements/data/measurements_repository.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredients_repository.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';

import 'support/fake_meal_planning_repository.dart';

void main() {
  test('missing measurement blocks calculated target setup', () async {
    final calls = <String>[];
    final service = _service(
      calls,
      targetExists: false,
      measurementExists: false,
    );

    expect(await service.check(), MealPlanningPrerequisiteIssue.measurement);
    expect(
      calls,
      containsAll([
        '/api/v1/me/profile',
        '/api/v1/me/dietary-preferences',
        '/api/v1/me/allergens',
        '/api/v1/me/disliked-ingredients',
        '/api/v1/me/pantry',
        '/api/v1/recipes',
        '/api/v1/me/nutrition-targets/current',
        '/api/v1/me/measurements/latest',
      ]),
    );
  });

  test('current target allows planning without a new measurement', () async {
    final calls = <String>[];
    final service = _service(calls);

    expect(await service.check(), isNull);
    expect(calls, isNot(contains('/api/v1/me/measurements/latest')));
  });

  test('empty recipe catalog blocks generation visibly', () async {
    final service = _service([], recipeCount: 0);
    expect(await service.check(), MealPlanningPrerequisiteIssue.recipes);
  });

  test('missing prerequisite prevents a generation POST', () async {
    final repository = FakeMealPlanningRepository();
    final controller = MealPlanningController(
      repository: repository,
      prerequisites: _service(
        [],
        targetExists: false,
        measurementExists: false,
      ),
    );
    addTearDown(controller.dispose);

    await controller.generate(generationRequest());

    expect(controller.state.status, MealPlanningStatus.missingPrerequisite);
    expect(
      controller.state.prerequisiteIssue,
      MealPlanningPrerequisiteIssue.measurement,
    );
    expect(repository.requests, isEmpty);
  });
}

MealPlanningPrerequisites _service(
  List<String> calls, {
  bool targetExists = true,
  bool measurementExists = true,
  int recipeCount = 18,
}) {
  final api = ApiClient(
    baseUrl: 'https://backend.test',
    httpClient: MockClient((request) async {
      final path = request.url.path;
      calls.add(path);
      Object? body;
      var status = 200;
      switch (path) {
        case '/api/v1/me/profile':
          body = {
            'householdSize': 1,
            'version': 1,
            'createdAt': '2026-01-01T00:00:00Z',
            'updatedAt': '2026-01-01T00:00:00Z',
          };
        case '/api/v1/me/dietary-preferences':
        case '/api/v1/me/allergens':
        case '/api/v1/me/disliked-ingredients':
        case '/api/v1/me/pantry':
          body = [];
        case '/api/v1/recipes':
          body = {
            'page': 0,
            'size': 1,
            'totalElements': recipeCount,
            'totalPages': recipeCount,
            'content': [],
          };
        case '/api/v1/me/nutrition-targets/current':
          if (targetExists) {
            body = {
              'nutrientValues': [
                {'nutrientCode': 'ENERGY'},
              ],
            };
          } else {
            status = 404;
            body = {'status': 404, 'code': 'NO_CURRENT_TARGET'};
          }
        case '/api/v1/me/measurements/latest':
          if (measurementExists) {
            body = {
              'measuredOn': '2026-01-01',
              'weightKg': 60,
              'bodyFatPercent': null,
              'waistCm': null,
              'source': 'USER_ENTERED',
              'note': null,
              'createdAt': '2026-01-01T00:00:00Z',
            };
          } else {
            status = 404;
            body = {'status': 404};
          }
        default:
          throw StateError('Unexpected request: $path');
      }
      return http.Response(
        jsonEncode(body),
        status,
        headers: {'content-type': 'application/json'},
      );
    }),
  );
  return MealPlanningPrerequisites(
    profile: HttpProfileRepository(api),
    measurements: HttpMeasurementsRepository(api),
    nutritionTarget: HttpNutritionTargetRepository(api),
    preferences: HttpPreferencesRepository(api),
    dislikedIngredients: HttpDislikedIngredientsRepository(api),
    pantry: HttpPantryRepository(api),
    recipes: HttpRecipeRepository(api),
  );
}
