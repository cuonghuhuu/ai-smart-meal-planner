import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredients_repository.dart';

void main() {
  test('GET uses the authenticated endpoint and parses both strengths', () async {
    late http.Request request;
    final repository = HttpDislikedIngredientsRepository(
      _api((incoming) async {
        request = incoming;
        return _jsonResponse(_preferencesPayload);
      }),
    );

    final preferences = await repository.getDislikedIngredients();

    expect(request.method, 'GET');
    expect(request.url.path, '/api/v1/me/disliked-ingredients');
    expect(request.headers['authorization'], 'Bearer access');
    expect(preferences, hasLength(2));
    expect(
      preferences[0].strength,
      DislikedIngredientStrength.dislike,
    );
    expect(preferences[1].strength, DislikedIngredientStrength.avoid);
    expect(preferences[0].note, isNull);
    expect(preferences[1].note, 'Kh\u00F4ng th\u00EDch m\u00F9i');
    expect(preferences[0].category?.code, 'GRAINS');
    expect(preferences[0].category?.displayName, 'Grains');
    expect(preferences[1].category, isNull);
  });

  test('PUT sends the complete replacement body with trimmed notes', () async {
    late http.Request request;
    final repository = HttpDislikedIngredientsRepository(
      _api((incoming) async {
        request = incoming;
        return _jsonResponse(_preferencesPayload);
      }),
    );

    final response = await repository.replaceDislikedIngredients([
      const DislikedIngredientSelection(
        ingredientPublicId: '00000000-0000-4000-8000-000000000101',
        strength: DislikedIngredientStrength.dislike,
        note: '  Kh\u00F4ng h\u1EE3p m\u00F9i  ',
      ),
      const DislikedIngredientSelection(
        ingredientPublicId: '00000000-0000-4000-8000-000000000102',
        strength: DislikedIngredientStrength.avoid,
        note: '   ',
      ),
    ]);
    final body = jsonDecode(request.body) as Map<String, dynamic>;

    expect(request.method, 'PUT');
    expect(request.url.path, '/api/v1/me/disliked-ingredients');
    expect(request.headers['authorization'], 'Bearer access');
    expect(body.keys.toList(), ['ingredients']);
    expect(body['ingredients'], [
      {
        'ingredientPublicId': '00000000-0000-4000-8000-000000000101',
        'strength': 'DISLIKE',
        'note': 'Kh\u00F4ng h\u1EE3p m\u00F9i',
      },
      {
        'ingredientPublicId': '00000000-0000-4000-8000-000000000102',
        'strength': 'AVOID',
        'note': null,
      },
    ]);
    expect(response[1].strength, DislikedIngredientStrength.avoid);
  });

  test('unknown strength fails with a response format exception', () async {
    final repository = HttpDislikedIngredientsRepository(
      _api((_) async => _jsonResponse(_unknownStrengthPayload)),
    );

    await expectLater(
      repository.getDislikedIngredients(),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });
}

ApiClient _api(Future<http.Response> Function(http.Request) handler) =>
    ApiClient(baseUrl: 'https://backend.test', httpClient: MockClient(handler))
      ..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );

http.Response _jsonResponse(Object value) => http.Response(
  jsonEncode(value),
  200,
  headers: const {'content-type': 'application/json'},
);

const _preferencesPayload = [
  {
    'ingredientPublicId': '00000000-0000-4000-8000-000000000101',
    'ingredientCode': 'ING_SMILING_VN_1001',
    'ingredientDisplayName': 'G\u1EA1o n\u1EBFp c\u00E1i',
    'category': {
      'code': 'GRAINS',
      'displayName': 'Grains',
      'parentCategoryCode': null,
      'description': null,
    },
    'strength': 'DISLIKE',
    'note': null,
  },
  {
    'ingredientPublicId': '00000000-0000-4000-8000-000000000102',
    'ingredientCode': 'ING_SMILING_VN_1002',
    'ingredientDisplayName': 'C\u00E0 chua',
    'category': null,
    'strength': 'AVOID',
    'note': 'Kh\u00F4ng th\u00EDch m\u00F9i',
  },
];

const _unknownStrengthPayload = [
  {
    'ingredientPublicId': '00000000-0000-4000-8000-000000000101',
    'ingredientCode': 'ING_SMILING_VN_1001',
    'ingredientDisplayName': 'G\u1EA1o n\u1EBFp c\u00E1i',
    'category': null,
    'strength': 'UNKNOWN',
    'note': null,
  },
];
