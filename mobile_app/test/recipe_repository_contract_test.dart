import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';

import 'recipe_models_test.dart' as fixture;

void main() {
  test('GET paths, filters, pagination, and authenticated requests', () async {
    final sent = <http.Request>[];
    final repository = _repository(
      sent,
      (request) => switch (request.url.path) {
        '/api/v1/recipes' => _json(fixture.pageJson),
        '/api/v1/recipes/${fixture.recipeId}' => _json(fixture.detailJson),
        '/api/v1/reference/recipe-tags' => _json([fixture.tagJson]),
        '/api/v1/reference/meal-slot-types' => _json([fixture.slotJson]),
        _ => throw StateError('Unexpected path ${request.url.path}'),
      },
    );

    await repository.getRecipes();
    await repository.getRecipes(query: '  phở & bò  ');
    await repository.getRecipes(mealSlotCode: ' DINNER ');
    await repository.getRecipes(tagCode: ' VEGAN ');
    await repository.getRecipes(maxMinutes: 0);
    await repository.getRecipes(
      query: ' soup ',
      mealSlotCode: ' DINNER ',
      tagCode: ' VEGAN ',
      maxMinutes: 30,
      page: 2,
      size: 5,
    );
    await repository.getRecipes(query: '  ', mealSlotCode: ' ', tagCode: '');
    final detail = await repository.getRecipe(' ${fixture.recipeId} ');
    final tags = await repository.getRecipeTags();
    final slots = await repository.getMealSlotTypes();

    expect(sent, hasLength(10));
    expect(sent.every((request) => request.method == 'GET'), isTrue);
    expect(
      sent.every(
        (request) => request.headers['authorization'] == 'Bearer access',
      ),
      isTrue,
    );
    expect(sent[0].url.path, '/api/v1/recipes');
    expect(sent[0].url.queryParameters, {'page': '0', 'size': '20'});
    expect(sent[1].url.queryParameters['q'], 'phở & bò');
    expect(sent[2].url.queryParameters['mealSlotCode'], 'DINNER');
    expect(sent[3].url.queryParameters['tagCode'], 'VEGAN');
    expect(sent[4].url.queryParameters['maxMinutes'], '0');
    expect(sent[5].url.queryParameters, {
      'page': '2',
      'size': '5',
      'q': 'soup',
      'mealSlotCode': 'DINNER',
      'tagCode': 'VEGAN',
      'maxMinutes': '30',
    });
    expect(sent[6].url.queryParameters, {'page': '0', 'size': '20'});
    expect(sent[7].url.path, '/api/v1/recipes/${fixture.recipeId}');
    expect(sent[8].url.path, '/api/v1/reference/recipe-tags');
    expect(sent[9].url.path, '/api/v1/reference/meal-slot-types');
    expect(detail.publicId, fixture.recipeId);
    expect(tags.single.code, 'VEGAN');
    expect(slots.single.code, 'DINNER');
  });

  test('encodes detail path segment safely', () async {
    final sent = <http.Request>[];
    final repository = _repository(sent, (_) => _json(fixture.detailJson));
    await repository.getRecipe(' id?x=1#fragment ');
    expect(sent.single.url.pathSegments.last, 'id?x=1#fragment');
    expect(sent.single.url.query, isEmpty);
    expect(sent.single.url.fragment, isEmpty);
  });

  test('invalid local arguments send no request', () async {
    final sent = <http.Request>[];
    final repository = _repository(sent, (_) => _json(fixture.pageJson));
    for (final call in <Future<Object?> Function()>[
      () => repository.getRecipes(page: -1),
      () => repository.getRecipes(size: 0),
      () => repository.getRecipes(size: 101),
      () => repository.getRecipes(maxMinutes: -1),
      () => repository.getRecipes(maxMinutes: 10081),
      () => repository.getRecipes(query: 'a' * 201),
      () => repository.getRecipe(' '),
      () => repository.getRecipe('a/b'),
    ]) {
      await expectLater(call(), throwsA(isA<ArgumentError>()));
    }
    expect(sent, isEmpty);
  });

  test('malformed successful payloads use response format exception', () async {
    final sent = <http.Request>[];
    final repository = _repository(sent, (_) => _json({'content': null}));
    await expectLater(
      repository.getRecipes(),
      throwsA(isA<ApiResponseFormatException>()),
    );
    await expectLater(
      repository.getRecipe(fixture.recipeId),
      throwsA(isA<ApiResponseFormatException>()),
    );
    await expectLater(
      repository.getRecipeTags(),
      throwsA(isA<ApiResponseFormatException>()),
    );
    await expectLater(
      repository.getMealSlotTypes(),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });

  test('HTTP error retains status and problem code', () async {
    final sent = <http.Request>[];
    final repository = _repository(
      sent,
      (_) => http.Response(
        jsonEncode({
          'status': 400,
          'code': 'INVALID_REQUEST',
          'detail': 'Invalid request',
        }),
        400,
      ),
    );
    await expectLater(
      repository.getRecipes(),
      throwsA(
        isA<ApiHttpException>()
            .having((error) => error.statusCode, 'statusCode', 400)
            .having((error) => error.problem?.code, 'code', 'INVALID_REQUEST'),
      ),
    );
  });

  test('transport failure remains a transport exception', () async {
    final api = ApiClient(
      baseUrl: 'https://backend.test',
      httpClient: MockClient(
        (_) async => throw http.ClientException('offline'),
      ),
    );
    final repository = HttpRecipeRepository(api);
    await expectLater(
      repository.getRecipes(),
      throwsA(
        isA<ApiTransportException>().having(
          (error) => error.kind,
          'kind',
          ApiTransportFailureKind.network,
        ),
      ),
    );
  });
}

HttpRecipeRepository _repository(
  List<http.Request> sent,
  http.Response Function(http.Request) respond,
) {
  final api =
      ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          sent.add(request);
          return respond(request);
        }),
      )..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );
  return HttpRecipeRepository(api);
}

http.Response _json(Object? value) => http.Response(jsonEncode(value), 200);
