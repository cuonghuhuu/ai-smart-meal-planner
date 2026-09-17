import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';

void main() {
  test(
    'dietary references use the exact authenticated endpoint and parse',
    () async {
      late http.Request request;
      final repository = HttpPreferencesRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response(_dietaryReferenceJson, 200);
        }),
      );

      final references = await repository.getDietaryPreferenceReferences();

      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/reference/dietary-preferences');
      expect(request.headers['authorization'], 'Bearer access');
      expect(references.single.code, 'VEGAN');
      expect(references.single.displayName, 'Vegan');
      expect(references.single.description, 'Plant-based meals.');
      expect(references.single.isExclusionary, isTrue);
      expect(references.single.displayOrder, 2);
    },
  );

  test(
    'selected dietary preferences use the exact endpoint and parse',
    () async {
      late http.Request request;
      final repository = HttpPreferencesRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response(_selectedDietaryJson, 200);
        }),
      );

      final selected = await repository.getSelectedDietaryPreferences();

      expect(request.method, 'GET');
      expect(request.url.path, '/api/v1/me/dietary-preferences');
      expect(selected.single.code, 'VEGAN');
      expect(selected.single.displayName, 'Vegan');
      expect(selected.single.description, 'Plant-based meals.');
      expect(selected.single.isExclusionary, isTrue);
    },
  );

  test('dietary replace sends exact key and backend codes', () async {
    late http.Request request;
    final repository = HttpPreferencesRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response(_selectedDietaryJson, 200);
      }),
    );

    final updated = await repository.replaceDietaryPreferences([
      'VEGAN',
      'LOW_CARB',
    ]);
    final body = jsonDecode(request.body) as Map<String, dynamic>;

    expect(request.method, 'PUT');
    expect(request.url.path, '/api/v1/me/dietary-preferences');
    expect(body.keys.toList(), ['preferences']);
    expect(body['preferences'], ['VEGAN', 'LOW_CARB']);
    expect(updated.single.code, 'VEGAN');
  });

  test('empty dietary replace serializes an empty list', () async {
    late http.Request request;
    final repository = HttpPreferencesRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response('[]', 200);
      }),
    );

    await repository.replaceDietaryPreferences(const []);

    final body = jsonDecode(request.body) as Map<String, dynamic>;
    expect(body, {'preferences': []});
  });

  test('allergen references and selected values use exact endpoints', () async {
    final paths = <String>[];
    final repository = HttpPreferencesRepository(
      _api((request) async {
        paths.add(request.url.path);
        if (request.url.path == '/api/v1/reference/allergens') {
          return http.Response(_allergenReferenceJson, 200);
        }
        return http.Response(_selectedAllergenJson, 200);
      }),
    );

    final references = await repository.getAllergenReferences();
    final selected = await repository.getSelectedAllergens();

    expect(paths, ['/api/v1/reference/allergens', '/api/v1/me/allergens']);
    expect(references.single.code, 'PEANUT');
    expect(references.single.displayName, 'Peanuts');
    expect(references.single.description, 'Peanuts and peanut products.');
    expect(references.single.displayOrder, 1);
    expect(selected.single.allergen, 'PEANUT');
    expect(selected.single.displayName, 'Peanuts');
    expect(selected.single.description, 'Peanuts and peanut products.');
    expect(selected.single.reactionKind, ReactionKind.allergy);
    expect(selected.single.note, 'Avoid cross-contact.');
  });

  test(
    'allergen replace sends exact item keys and explicit reaction',
    () async {
      late http.Request request;
      final repository = HttpPreferencesRepository(
        _api((incoming) async {
          request = incoming;
          return http.Response(_selectedAllergenJson, 200);
        }),
      );

      final updated = await repository.replaceAllergens([
        const AllergenSelection(
          allergen: 'PEANUT',
          reactionKind: ReactionKind.intolerance,
          note: '  Avoid restaurants.  ',
        ),
      ]);
      final body = jsonDecode(request.body) as Map<String, dynamic>;

      expect(request.method, 'PUT');
      expect(request.url.path, '/api/v1/me/allergens');
      expect(body.keys.toList(), ['allergens']);
      expect(body['allergens'], [
        {
          'allergen': 'PEANUT',
          'reactionKind': 'INTOLERANCE',
          'note': 'Avoid restaurants.',
        },
      ]);
      expect(updated.single.reactionKind, ReactionKind.allergy);
    },
  );

  test('blank allergen note serializes as null', () async {
    late http.Request request;
    final repository = HttpPreferencesRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response('[]', 200);
      }),
    );

    await repository.replaceAllergens([
      const AllergenSelection(
        allergen: 'PEANUT',
        reactionKind: ReactionKind.unspecified,
        note: '   ',
      ),
    ]);

    final body = jsonDecode(request.body) as Map<String, dynamic>;
    expect(body['allergens'], [
      {'allergen': 'PEANUT', 'reactionKind': 'UNSPECIFIED', 'note': null},
    ]);
  });

  test('empty allergen replace serializes an empty list', () async {
    late http.Request request;
    final repository = HttpPreferencesRepository(
      _api((incoming) async {
        request = incoming;
        return http.Response('[]', 200);
      }),
    );

    await repository.replaceAllergens(const []);

    final body = jsonDecode(request.body) as Map<String, dynamic>;
    expect(body, {'allergens': []});
  });

  test('allergen 409 is not retried by the repository client', () async {
    var calls = 0;
    var refreshCalls = 0;
    final api = ApiClient(
      baseUrl: 'https://backend.test',
      httpClient: MockClient((_) async {
        calls++;
        return http.Response('', 409);
      }),
    );
    api.configureAuthentication(
      accessTokenProvider: () => 'access',
      refreshAccessToken: () async {
        refreshCalls++;
        return true;
      },
    );
    final repository = HttpPreferencesRepository(api);

    expect(
      repository.replaceAllergens(const []),
      throwsA(
        isA<ApiHttpException>().having(
          (error) => error.statusCode,
          'status',
          409,
        ),
      ),
    );
    await Future<void>.delayed(Duration.zero);
    expect(calls, 1);
    expect(refreshCalls, 0);
  });
}

ApiClient _api(Future<http.Response> Function(http.Request) handler) =>
    ApiClient(baseUrl: 'https://backend.test', httpClient: MockClient(handler))
      ..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );

const _dietaryReferenceJson =
    '[{"code":"VEGAN","displayName":"Vegan",'
    '"description":"Plant-based meals.","isExclusionary":true,'
    '"displayOrder":2}]';

const _selectedDietaryJson =
    '[{"code":"VEGAN","displayName":"Vegan",'
    '"description":"Plant-based meals.","isExclusionary":true}]';

const _allergenReferenceJson =
    '[{"code":"PEANUT","displayName":"Peanuts",'
    '"description":"Peanuts and peanut products.","displayOrder":1}]';

const _selectedAllergenJson =
    '[{"allergen":"PEANUT","displayName":"Peanuts",'
    '"description":"Peanuts and peanut products.",'
    '"reactionKind":"ALLERGY","note":"Avoid cross-contact."}]';
