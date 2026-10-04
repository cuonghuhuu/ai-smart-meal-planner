import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_controller.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';

import 'support/fake_meal_planning_repository.dart';

void main() {
  test('SUCCEEDED generates then reads persisted plan', () async {
    final repository = FakeMealPlanningRepository();
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);
    final statuses = <MealPlanningStatus>[];
    controller.addListener(() => statuses.add(controller.state.status));

    await controller.generate(generationRequest());

    expect(statuses, [
      MealPlanningStatus.generating,
      MealPlanningStatus.loadingPlan,
      MealPlanningStatus.loaded,
    ]);
    expect(repository.requests, hasLength(1));
    expect(repository.readIds, [planId]);
    expect(
      controller.state.plan!.entries.single.recipeTitle,
      'Oatmeal with Banana',
    );
  });

  test('DEGRADED reads persisted entries and gaps', () async {
    final repository = FakeMealPlanningRepository();
    repository.onGenerate = (_) async =>
        generated(status: MealPlanGenerationStatus.degraded);
    repository.onGetPlan = (_) async =>
        persistedPlan(status: MealPlanGenerationStatus.degraded);
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);

    await controller.generate(generationRequest());

    expect(controller.state.status, MealPlanningStatus.loaded);
    expect(controller.state.plan!.status, MealPlanGenerationStatus.degraded);
    expect(controller.state.plan!.entries, hasLength(1));
    expect(controller.state.plan!.unfilledSlots, hasLength(1));
  });

  test('INFEASIBLE never reads and allows another generation', () async {
    final repository = FakeMealPlanningRepository()
      ..onGenerate = (_) async =>
          generated(status: MealPlanGenerationStatus.infeasible);
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);

    await controller.generate(generationRequest());
    expect(controller.state.status, MealPlanningStatus.infeasible);
    expect(repository.readIds, isEmpty);
    await controller.generate(generationRequest());
    expect(repository.requests, hasLength(2));
  });

  test('failed read preserves ID; retry reads same ID without POST', () async {
    var reads = 0;
    final repository = FakeMealPlanningRepository()
      ..onGetPlan = (_) async {
        reads++;
        if (reads == 1) throw const ApiHttpException(503);
        return persistedPlan();
      };
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);

    await controller.generate(generationRequest());
    expect(controller.state.status, MealPlanningStatus.error);
    expect(controller.state.pendingMealPlanPublicId, planId);
    expect(controller.state.canRetryLoad, isTrue);
    await controller.generate(generationRequest());
    expect(repository.requests, hasLength(1));
    await controller.retryLoad();

    expect(repository.readIds, [planId, planId]);
    expect(repository.requests, hasLength(1));
    expect(controller.state.status, MealPlanningStatus.loaded);
  });

  test('concurrent Generate calls make one POST', () async {
    final pending = Completer<MealPlanGenerationResponse>();
    final repository = FakeMealPlanningRepository()
      ..onGenerate = (_) => pending.future;
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);

    final first = controller.generate(generationRequest());
    await controller.generate(generationRequest());
    expect(repository.requests, hasLength(1));
    expect(controller.state.status, MealPlanningStatus.generating);
    pending.complete(generated());
    await first;
    expect(repository.readIds, [planId]);
  });

  test('session reset ignores an in-flight generation result', () async {
    final pending = Completer<MealPlanGenerationResponse>();
    final repository = FakeMealPlanningRepository()
      ..onGenerate = (_) => pending.future;
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);

    final operation = controller.generate(generationRequest());
    controller.resetForSessionChange();
    pending.complete(generated());
    await operation;

    expect(controller.state.status, MealPlanningStatus.idle);
    expect(repository.readIds, isEmpty);
  });

  test('generation error is safe and retryable', () async {
    final repository = FakeMealPlanningRepository()
      ..onGenerate = (_) async =>
          throw const ApiTransportException(ApiTransportFailureKind.network);
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);

    await controller.generate(generationRequest());
    expect(controller.state.status, MealPlanningStatus.error);
    expect(controller.state.pendingMealPlanPublicId, isNull);
    expect(controller.state.errorMessage, isNotEmpty);
    await controller.generate(generationRequest());
    expect(repository.requests, hasLength(2));
  });
}
