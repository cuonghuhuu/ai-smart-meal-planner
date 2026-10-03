import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_planning_repository.dart';

const _requestId = '11111111-1111-4111-8111-111111111111';
const _planId = '22222222-2222-4222-8222-222222222222';
const _recipeId = '33333333-3333-4333-8333-333333333333';

void main() {
  test(
    'generate POST sends authenticated contract body and web CSRF',
    () async {
      late http.Request sent;
      var csrfCalls = 0;
      final repository = HttpMealPlanningRepository(
        _api((request) async {
          sent = request;
          return http.Response(_generationJson('SUCCEEDED', _planId), 200);
        }),
        csrfTokenProvider: () async {
          csrfCalls++;
          return const CsrfToken(headerName: 'X-XSRF-TOKEN', value: 'csrf');
        },
      );

      final result = await repository.generate(_request());

      expect(sent.method, 'POST');
      expect(sent.url.path, '/api/v1/me/meal-plans/generate');
      expect(sent.headers['authorization'], 'Bearer access');
      expect(sent.headers['x-xsrf-token'], 'csrf');
      expect(jsonDecode(sent.body), _request().toJson());
      expect(csrfCalls, 1);
      expect(result.mealPlanPublicId, _planId);
    },
  );

  test('GET uses returned public ID and authenticated endpoint', () async {
    late http.Request sent;
    final repository = HttpMealPlanningRepository(
      _api((request) async {
        sent = request;
        return http.Response(_persistedJson, 200);
      }),
    );

    final plan = await repository.getPlan(_planId);

    expect(sent.method, 'GET');
    expect(sent.url.path, '/api/v1/me/meal-plans/$_planId');
    expect(sent.headers['authorization'], 'Bearer access');
    expect(plan.entries.single.recipePublicId, _recipeId);
    expect(
      plan.unfilledSlots.single.reasonCode,
      UnfilledSlotReasonCode.noEligibleRecipe,
    );
  });

  test('INFEASIBLE exposes no plan ID and requires no GET', () async {
    final methods = <String>[];
    final repository = HttpMealPlanningRepository(
      _api((request) async {
        methods.add(request.method);
        return http.Response(_generationJson('INFEASIBLE', null), 200);
      }),
    );

    final result = await repository.generate(_request());

    expect(result.status, MealPlanGenerationStatus.infeasible);
    expect(result.mealPlanPublicId, isNull);
    expect(methods, ['POST']);
  });

  test(
    'failed GET can retry the same DEGRADED plan without another POST',
    () async {
      var postCalls = 0;
      var getCalls = 0;
      final repository = HttpMealPlanningRepository(
        _api((request) async {
          if (request.method == 'POST') {
            postCalls++;
            return http.Response(_generationJson('DEGRADED', _planId), 200);
          }
          expect(request.url.path, '/api/v1/me/meal-plans/$_planId');
          getCalls++;
          if (getCalls == 1) return http.Response('', 503);
          return http.Response(_persistedJson, 200);
        }),
      );

      final generated = await repository.generate(_request());
      final retryId = generated.mealPlanPublicId!;
      await expectLater(
        repository.getPlan(retryId),
        throwsA(
          isA<ApiHttpException>().having((e) => e.statusCode, 'status', 503),
        ),
      );
      final plan = await repository.getPlan(retryId);

      expect(generated.status, MealPlanGenerationStatus.degraded);
      expect(plan.mealPlanPublicId, retryId);
      expect(plan.unfilledSlots, hasLength(1));
      expect(postCalls, 1);
      expect(getCalls, 2);
    },
  );

  test('malformed API response fails with shared format exception', () async {
    final repository = HttpMealPlanningRepository(
      _api((_) async => http.Response('{"status":"SUCCEEDED"}', 200)),
    );

    await expectLater(
      repository.generate(_request()),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });
}

MealPlanGenerationRequest _request() => MealPlanGenerationRequest(
  startDate: DateTime(2026, 10, 5),
  days: 7,
  requestedMealSlots: [MealSlotCode.breakfast, MealSlotCode.dinner],
  defaultServings: 2,
  maxMinutesPerMeal: 30,
);

ApiClient _api(Future<http.Response> Function(http.Request) handler) =>
    ApiClient(baseUrl: 'https://backend.test', httpClient: MockClient(handler))
      ..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );

String _generationJson(String status, String? planId) {
  final body = <String, Object>{
    'requestPublicId': _requestId,
    'status': status,
  };
  if (planId != null) body['mealPlanPublicId'] = planId;
  return jsonEncode(body);
}

final _persistedJson = jsonEncode({
  'mealPlanPublicId': _planId,
  'requestPublicId': _requestId,
  'status': 'DEGRADED',
  'startDate': '2026-10-05',
  'endDate': '2026-10-11',
  'defaultServings': 2.0,
  'entries': [
    {
      'planDate': '2026-10-05',
      'mealSlotCode': 'BREAKFAST',
      'recipePublicId': _recipeId,
      'recipeTitle': 'Oatmeal with Banana',
      'servings': 2.0,
    },
  ],
  'unfilledSlots': [
    {
      'planDate': '2026-10-06',
      'mealSlotCode': 'DINNER',
      'reasonCode': 'NO_ELIGIBLE_RECIPE',
      'explanation': 'No eligible recipe',
    },
  ],
});
