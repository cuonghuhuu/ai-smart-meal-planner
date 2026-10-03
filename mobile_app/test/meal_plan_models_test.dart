import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';

const requestId = '11111111-1111-4111-8111-111111111111';
const planId = '22222222-2222-4222-8222-222222222222';
const recipeId = '33333333-3333-4333-8333-333333333333';

void main() {
  test('generation request uses date-only and contract enum values', () {
    final request = MealPlanGenerationRequest(
      startDate: DateTime(2026, 10, 5),
      days: 7,
      requestedMealSlots: [MealSlotCode.breakfast, MealSlotCode.dinner],
      defaultServings: 2,
      maxMinutesPerMeal: 30,
    );

    expect(request.toJson(), {
      'startDate': '2026-10-05',
      'days': 7,
      'requestedMealSlots': ['BREAKFAST', 'DINNER'],
      'defaultServings': 2.0,
      'maxMinutesPerMeal': 30,
    });
    expect(
      MealPlanGenerationRequest(
        startDate: DateTime(2026, 10, 5),
        days: 1,
        requestedMealSlots: [MealSlotCode.lunch],
        defaultServings: 1,
      ).toJson().containsKey('maxMinutesPerMeal'),
      isFalse,
    );
  });

  for (final status in ['SUCCEEDED', 'DEGRADED']) {
    test('$status generation response preserves retryable plan ID', () {
      final result = MealPlanGenerationResponse.fromJson({
        'requestPublicId': requestId,
        'status': status,
        'mealPlanPublicId': planId,
      });
      expect(result.requestPublicId, requestId);
      expect(result.status.wireValue, status);
      expect(result.mealPlanPublicId, planId);
    });
  }

  test('INFEASIBLE accepts absent or null plan ID', () {
    for (final value in [
      {'requestPublicId': requestId, 'status': 'INFEASIBLE'},
      {
        'requestPublicId': requestId,
        'status': 'INFEASIBLE',
        'mealPlanPublicId': null,
      },
    ]) {
      final result = MealPlanGenerationResponse.fromJson(value);
      expect(result.status, MealPlanGenerationStatus.infeasible);
      expect(result.mealPlanPublicId, isNull);
    }
  });

  test('persisted DEGRADED plan parses entries and unfilled slots', () {
    final plan = PersistedMealPlan.fromJson(_planJson);
    expect(plan.mealPlanPublicId, planId);
    expect(plan.requestPublicId, requestId);
    expect(plan.status, MealPlanGenerationStatus.degraded);
    expect(formatMealPlanDate(plan.startDate), '2026-10-05');
    expect(formatMealPlanDate(plan.endDate), '2026-10-11');
    expect(plan.defaultServings, 2);
    expect(plan.entries.single.planDate, DateTime(2026, 10, 5));
    expect(plan.entries.single.mealSlotCode, MealSlotCode.breakfast);
    expect(plan.entries.single.recipePublicId, recipeId);
    expect(plan.entries.single.recipeTitle, 'Oatmeal with Banana');
    expect(plan.entries.single.servings, 2);
    expect(plan.unfilledSlots.single.planDate, DateTime(2026, 10, 6));
    expect(plan.unfilledSlots.single.mealSlotCode, MealSlotCode.dinner);
    expect(
      plan.unfilledSlots.single.reasonCode,
      UnfilledSlotReasonCode.noEligibleRecipe,
    );
    expect(plan.unfilledSlots.single.explanation, 'No eligible recipe');
    expect(() => plan.entries.add(plan.entries.single), throwsUnsupportedError);
  });

  test('nullable unfilled explanation and empty collections parse', () {
    final slotJson =
        (_planJson['unfilledSlots'] as List).single as Map<String, Object?>;
    final plan = PersistedMealPlan.fromJson({
      ..._planJson,
      'entries': [],
      'unfilledSlots': [
        {...slotJson, 'explanation': null},
      ],
    });
    expect(plan.entries, isEmpty);
    expect(plan.unfilledSlots.single.explanation, isNull);
  });

  test('missing or unknown required response values fail predictably', () {
    final entryJson =
        (_planJson['entries'] as List).single as Map<String, Object?>;
    final slotJson =
        (_planJson['unfilledSlots'] as List).single as Map<String, Object?>;
    final badGeneration = [
      {'status': 'SUCCEEDED', 'mealPlanPublicId': planId},
      {'requestPublicId': requestId, 'status': 'SUCCEEDED'},
      {'requestPublicId': requestId, 'status': 'UNKNOWN'},
      {
        'requestPublicId': requestId,
        'status': 'INFEASIBLE',
        'mealPlanPublicId': planId,
      },
    ];
    for (final value in badGeneration) {
      expect(
        () => MealPlanGenerationResponse.fromJson(value),
        throwsA(isA<ApiResponseFormatException>()),
      );
    }

    final badPlans = [
      {..._planJson, 'mealPlanPublicId': null},
      {..._planJson, 'status': 'INFEASIBLE'},
      {..._planJson, 'startDate': '2026-02-30'},
      {..._planJson, 'entries': null},
      {
        ..._planJson,
        'entries': [
          {...entryJson, 'mealSlotCode': 'UNKNOWN'},
        ],
      },
      {
        ..._planJson,
        'unfilledSlots': [
          {...slotJson, 'reasonCode': 'UNKNOWN'},
        ],
      },
    ];
    for (final value in badPlans) {
      expect(
        () => PersistedMealPlan.fromJson(value),
        throwsA(isA<ApiResponseFormatException>()),
      );
    }
  });
}

const _planJson = <String, Object?>{
  'mealPlanPublicId': planId,
  'requestPublicId': requestId,
  'status': 'DEGRADED',
  'startDate': '2026-10-05',
  'endDate': '2026-10-11',
  'defaultServings': 2.0,
  'entries': [
    {
      'planDate': '2026-10-05',
      'mealSlotCode': 'BREAKFAST',
      'recipePublicId': recipeId,
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
};
