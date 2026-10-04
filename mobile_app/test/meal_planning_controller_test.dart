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

  test('loaded plan reads and stores its shopping list once', () async {
    final repository = FakeMealPlanningRepository();
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);
    await controller.generate(generationRequest());

    expect(controller.state.canLoadShoppingList, isTrue);
    await controller.loadShoppingList();
    await controller.loadShoppingList();

    expect(repository.shoppingListReadIds, [planId]);
    expect(controller.state.status, MealPlanningStatus.loaded);
    expect(controller.state.plan!.mealPlanPublicId, planId);
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.loaded);
    expect(controller.state.shoppingList!.mealPlanPublicId, planId);
    expect(controller.state.shoppingList!.items.single.quantityToBuy, 250);
    expect(controller.state.shoppingListErrorMessage, isNull);
  });

  test('concurrent shopping-list loads make one read', () async {
    final pending = Completer<MealPlanShoppingList>();
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) => pending.future;
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);
    await controller.generate(generationRequest());

    final first = controller.loadShoppingList();
    await controller.loadShoppingList();
    expect(repository.shoppingListReadIds, [planId]);
    expect(controller.state.status, MealPlanningStatus.loaded);
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.loading);
    expect(controller.state.isShoppingListBusy, isTrue);

    pending.complete(shoppingList());
    await first;
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.loaded);
  });

  test(
    'shopping-list failure keeps the plan and hides problem detail',
    () async {
      final repository = FakeMealPlanningRepository()
        ..onGetShoppingList = (_) async => throw const ApiHttpException(
          400,
          problem: ApiProblem(
            status: 400,
            code: 'SHOPPING_LIST_FAILED',
            detail: 'private backend detail',
          ),
        );
      final controller = MealPlanningController(repository: repository);
      addTearDown(controller.dispose);
      await controller.generate(generationRequest());

      await controller.loadShoppingList();

      expect(controller.state.status, MealPlanningStatus.loaded);
      expect(controller.state.plan!.mealPlanPublicId, planId);
      expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.error);
      expect(controller.state.shoppingList, isNull);
      expect(controller.state.shoppingListErrorMessage, isNotEmpty);
      expect(
        controller.state.shoppingListErrorMessage,
        isNot(contains('private backend detail')),
      );
      expect(controller.state.canRetryShoppingList, isTrue);
    },
  );

  test(
    'shopping-list retry reads the same plan without another POST',
    () async {
      var reads = 0;
      final repository = FakeMealPlanningRepository()
        ..onGetShoppingList = (_) async {
          reads++;
          if (reads == 1) throw const ApiHttpException(503);
          return shoppingList();
        };
      final controller = MealPlanningController(repository: repository);
      addTearDown(controller.dispose);
      await controller.generate(generationRequest());

      await controller.loadShoppingList();
      await controller.loadShoppingList();
      expect(repository.shoppingListReadIds, [planId]);
      await controller.retryShoppingList();

      expect(repository.shoppingListReadIds, [planId, planId]);
      expect(repository.requests, hasLength(1));
      expect(repository.readIds, [planId]);
      expect(controller.state.status, MealPlanningStatus.loaded);
      expect(
        controller.state.shoppingListStatus,
        ShoppingListLoadStatus.loaded,
      );
      expect(controller.state.shoppingList!.mealPlanPublicId, planId);
      expect(controller.state.shoppingListErrorMessage, isNull);
    },
  );

  test('session reset ignores an in-flight shopping-list result', () async {
    final pending = Completer<MealPlanShoppingList>();
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) => pending.future;
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);
    await controller.generate(generationRequest());

    final operation = controller.loadShoppingList();
    controller.resetForSessionChange();
    pending.complete(shoppingList());
    await operation;

    expect(controller.state.status, MealPlanningStatus.idle);
    expect(controller.state.plan, isNull);
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.idle);
    expect(controller.state.shoppingList, isNull);
    expect(controller.state.shoppingListErrorMessage, isNull);
  });

  test('new generation clears an earlier shopping list immediately', () async {
    final repository = FakeMealPlanningRepository();
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);
    await controller.generate(generationRequest());
    await controller.loadShoppingList();
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.loaded);

    final pending = Completer<MealPlanGenerationResponse>();
    repository.onGenerate = (_) => pending.future;
    final operation = controller.generate(generationRequest());
    expect(controller.state.status, MealPlanningStatus.generating);
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.idle);
    expect(controller.state.shoppingList, isNull);

    pending.complete(generated());
    await operation;
    expect(controller.state.status, MealPlanningStatus.loaded);
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.idle);
    expect(controller.state.shoppingList, isNull);
    expect(repository.shoppingListReadIds, [planId]);
  });

  test(
    'shopping-list load without a persisted plan performs no read',
    () async {
      final repository = FakeMealPlanningRepository();
      final controller = MealPlanningController(repository: repository);
      addTearDown(controller.dispose);

      await controller.loadShoppingList();
      await controller.retryShoppingList();

      expect(repository.shoppingListReadIds, isEmpty);
      expect(controller.state.status, MealPlanningStatus.idle);
      expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.idle);
    },
  );

  test('shopping-list response for another plan fails closed', () async {
    final fixture = shoppingList();
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) async => MealPlanShoppingList(
        mealPlanPublicId: requestId,
        status: fixture.status,
        items: fixture.items,
        unquantifiedItems: fixture.unquantifiedItems,
      );
    final controller = MealPlanningController(repository: repository);
    addTearDown(controller.dispose);
    await controller.generate(generationRequest());

    await controller.loadShoppingList();

    expect(controller.state.status, MealPlanningStatus.loaded);
    expect(controller.state.plan!.mealPlanPublicId, planId);
    expect(controller.state.shoppingListStatus, ShoppingListLoadStatus.error);
    expect(controller.state.shoppingList, isNull);
    expect(controller.state.shoppingListErrorMessage, isNotEmpty);
  });
}
