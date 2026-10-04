import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_controller.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_pantry_repository.dart';
import 'support/fake_meal_planning_repository.dart';

void main() {
  late FakePantryRepository repository;
  late PantryController controller;
  late int inventoryChanges;

  setUp(() {
    repository = FakePantryRepository();
    inventoryChanges = 0;
    controller = PantryController(
      repository: repository,
      onInventoryChanged: () => inventoryChanges++,
    );
  });
  tearDown(() => controller.dispose());

  Future<void> loadLot({
    String initial = '10',
    String remaining = '5',
    PantryItemStatus status = PantryItemStatus.available,
    bool includeClosed = false,
  }) async {
    final item = pantryItem(
      quantityInitial: initial,
      quantityRemaining: remaining,
      status: status,
    );
    repository.items = [item];
    repository.detail = item;
    await controller.loadInitial();
    if (includeClosed) await controller.setIncludeClosed(true);
    await controller.loadDetail(testPantryId);
  }

  test(
    'adjust uses exact signed bounds and adopts returned detail/list row',
    () async {
      await loadLot();
      expect(
        await controller.adjustItem(
          testPantryId,
          const AdjustPantryItemRequest(quantityDelta: '5.0001'),
        ),
        isNull,
      );
      expect(
        await controller.adjustItem(
          testPantryId,
          const AdjustPantryItemRequest(quantityDelta: '-5'),
        ),
        isNull,
      );
      expect(
        await controller.adjustItem(
          testPantryId,
          const AdjustPantryItemRequest(quantityDelta: '0'),
        ),
        isNull,
      );
      expect(repository.adjustRequests, isEmpty);
      repository.onAdjust = (_, _) async => pantryItem(
        quantityInitial: '10',
        quantityRemaining: '10',
        note: 'metadata',
      );
      final result = await controller.adjustItem(
        testPantryId,
        const AdjustPantryItemRequest(quantityDelta: '5', note: ' correction '),
      );
      expect(result?.quantityRemaining.toString(), '10');
      expect(controller.detailState.item, same(result));
      expect(controller.listState.items.single, same(result));
      expect(
        repository.adjustRequests.single.$2.toJson()['note'],
        'correction',
      );
      expect(controller.detailState.item?.note, 'metadata');
      expect(inventoryChanges, 1);
    },
  );

  test('negative 0.0001 correction stays positive', () async {
    await loadLot(initial: '1', remaining: '1.0000');
    repository.onAdjust = (_, _) async =>
        pantryItem(quantityInitial: '1', quantityRemaining: '0.9999');
    final result = await controller.adjustItem(
      testPantryId,
      const AdjustPantryItemRequest(quantityDelta: '-0.0001'),
    );
    expect(result?.quantityRemaining.toString(), '0.9999');
    expect(repository.adjustRequests, hasLength(1));
  });

  test(
    'partial consume updates row; exact remaining closes open lot',
    () async {
      await loadLot();
      repository.onConsume = (_, _) async =>
          pantryItem(quantityInitial: '10', quantityRemaining: '4.9999');
      final partial = await controller.consumeItem(
        testPantryId,
        const ConsumePantryItemRequest(quantity: '0.0001'),
      );
      expect(partial?.quantityRemaining.toString(), '4.9999');
      expect(controller.listState.items.single, same(partial));
      expect(
        await controller.consumeItem(
          testPantryId,
          const ConsumePantryItemRequest(quantity: '5'),
        ),
        isNull,
      );
      expect(repository.consumeRequests, hasLength(1));

      repository.onConsume = (_, _) async => pantryItem(
        quantityInitial: '10',
        quantityRemaining: '0',
        status: PantryItemStatus.consumed,
        closedAt: DateTime(2026, 1, 2),
      );
      final closed = await controller.consumeItem(
        testPantryId,
        const ConsumePantryItemRequest(quantity: '4.9999'),
      );
      expect(closed?.status, PantryItemStatus.consumed);
      expect(controller.detailState.item, same(closed));
      expect(controller.listState.items, isEmpty);
      expect(inventoryChanges, 2);
    },
  );

  test('closed consumed lot stays in include-closed list', () async {
    await loadLot(includeClosed: true);
    repository.onConsume = (_, _) async => pantryItem(
      quantityInitial: '10',
      quantityRemaining: '0',
      status: PantryItemStatus.consumed,
      closedAt: DateTime(2026, 1, 2),
    );
    final result = await controller.consumeItem(
      testPantryId,
      const ConsumePantryItemRequest(quantity: '5'),
    );
    expect(controller.listState.includeClosed, isTrue);
    expect(controller.listState.items.single, same(result));
  });

  for (final status in [
    PantryItemStatus.available,
    PantryItemStatus.reserved,
  ]) {
    test('$status discard closes all remaining and removes open row', () async {
      await loadLot(status: status);
      repository.onDiscard = (_, _) async => pantryItem(
        quantityInitial: '10',
        quantityRemaining: '0',
        status: PantryItemStatus.discarded,
        closedAt: DateTime(2026, 1, 2),
      );
      final result = await controller.discardItem(
        testPantryId,
        const DiscardPantryItemRequest(note: ' damaged '),
      );
      expect(result?.status, PantryItemStatus.discarded);
      expect(result?.closedAt, isNotNull);
      expect(controller.detailState.item, same(result));
      expect(controller.listState.items, isEmpty);
      expect(repository.discardRequests.single.$2.toJson(), {
        'note': 'damaged',
      });
      expect(inventoryChanges, 1);
    });
  }

  test('discarded lot remains in include-closed history', () async {
    await loadLot(status: PantryItemStatus.reserved, includeClosed: true);
    repository.onDiscard = (_, _) async => pantryItem(
      quantityInitial: '10',
      quantityRemaining: '0',
      status: PantryItemStatus.discarded,
      closedAt: DateTime(2026, 1, 2),
    );
    final result = await controller.discardItem(
      testPantryId,
      const DiscardPantryItemRequest(),
    );
    expect(controller.listState.items.single, same(result));
    expect(controller.listState.includeClosed, isTrue);
  });

  for (final status in [
    PantryItemStatus.consumed,
    PantryItemStatus.discarded,
    PantryItemStatus.expired,
  ]) {
    test('$status cannot start any quantity action', () async {
      await loadLot(status: status);
      expect(
        await controller.adjustItem(
          testPantryId,
          const AdjustPantryItemRequest(quantityDelta: '1'),
        ),
        isNull,
      );
      expect(
        await controller.consumeItem(
          testPantryId,
          const ConsumePantryItemRequest(quantity: '1'),
        ),
        isNull,
      );
      expect(
        await controller.discardItem(
          testPantryId,
          const DiscardPantryItemRequest(),
        ),
        isNull,
      );
      expect(repository.adjustRequests, isEmpty);
      expect(repository.consumeRequests, isEmpty);
      expect(repository.discardRequests, isEmpty);
      expect(inventoryChanges, 0);
    });
  }

  test('duplicate submission is blocked and failure maps safely', () async {
    await loadLot();
    final pending = Completer<PantryItem>();
    repository.onAdjust = (_, _) => pending.future;
    final first = controller.adjustItem(
      testPantryId,
      const AdjustPantryItemRequest(quantityDelta: '1'),
    );
    expect(controller.mutationState.status, PantryMutationStatus.submitting);
    expect(
      await controller.adjustItem(
        testPantryId,
        const AdjustPantryItemRequest(quantityDelta: '1'),
      ),
      isNull,
    );
    expect(repository.adjustRequests, hasLength(1));
    pending.completeError(
      const ApiHttpException(
        409,
        problem: ApiProblem(status: 409, code: 'CONFLICT', detail: 'private'),
      ),
    );
    expect(await first, isNull);
    expect(controller.mutationState.errorMessage, AppStrings.pantryConflict);
    expect(inventoryChanges, 0);
  });

  test(
    'ITEM_NOT_OPEN error is safe and reloads authoritative detail',
    () async {
      await loadLot();
      repository.discardError = const ApiHttpException(
        409,
        problem: ApiProblem(
          status: 409,
          code: 'ITEM_NOT_OPEN',
          detail: 'private',
        ),
      );
      repository.detail = pantryItem(
        quantityInitial: '10',
        quantityRemaining: '0',
        status: PantryItemStatus.discarded,
      );
      expect(
        await controller.discardItem(
          testPantryId,
          const DiscardPantryItemRequest(),
        ),
        isNull,
      );
      await Future<void>.delayed(Duration.zero);
      expect(
        controller.mutationState.errorMessage,
        AppStrings.pantryItemNotOpen,
      );
      expect(controller.detailState.item?.status, PantryItemStatus.discarded);
      expect(inventoryChanges, 0);
    },
  );

  test('old list result cannot restore a consumed closed lot', () async {
    await loadLot();
    final pending = Completer<List<PantryItem>>();
    repository.onList = (_) => pending.future;
    final refresh = controller.refreshList();
    repository.onConsume = (_, _) async => pantryItem(
      quantityInitial: '10',
      quantityRemaining: '0',
      status: PantryItemStatus.consumed,
    );
    await controller.consumeItem(
      testPantryId,
      const ConsumePantryItemRequest(quantity: '5'),
    );
    pending.complete([
      pantryItem(quantityInitial: '10', quantityRemaining: '5'),
    ]);
    await refresh;
    expect(controller.listState.items, isEmpty);
  });

  for (final action in ['adjust', 'consume', 'discard']) {
    test('$action ignores result after session/account reset', () async {
      await loadLot();
      final pending = Completer<PantryItem>();
      repository.onAdjust = (_, _) => pending.future;
      repository.onConsume = (_, _) => pending.future;
      repository.onDiscard = (_, _) => pending.future;
      final Future<PantryItem?> operation = switch (action) {
        'adjust' => controller.adjustItem(
          testPantryId,
          const AdjustPantryItemRequest(quantityDelta: '1'),
        ),
        'consume' => controller.consumeItem(
          testPantryId,
          const ConsumePantryItemRequest(quantity: '1'),
        ),
        _ => controller.discardItem(
          testPantryId,
          const DiscardPantryItemRequest(),
        ),
      };
      controller.resetForSessionChange();
      repository.items = [pantryItem(publicId: testPantryIdTwo)];
      await controller.loadInitial();
      pending.complete(
        pantryItem(quantityInitial: '10', quantityRemaining: '6'),
      );
      expect(await operation, isNull);
      expect(controller.listState.items.single.publicId, testPantryIdTwo);
      expect(controller.detailState.item, isNull);
      expect(inventoryChanges, 0);
    });
  }

  test('action for detail A cannot overwrite newer detail B', () async {
    await loadLot();
    final pending = Completer<PantryItem>();
    repository.onAdjust = (_, _) => pending.future;
    final action = controller.adjustItem(
      testPantryId,
      const AdjustPantryItemRequest(quantityDelta: '1'),
    );
    repository.detail = pantryItem(publicId: testPantryIdTwo);
    await controller.loadDetail(testPantryIdTwo);
    pending.complete(pantryItem(quantityInitial: '10', quantityRemaining: '6'));
    expect(await action, isNull);
    expect(controller.detailState.item?.publicId, testPantryIdTwo);
    expect(inventoryChanges, 0);
  });

  test(
    'create and quantity actions invalidate; metadata and failures do not',
    () async {
      await loadLot();
      const create = CreatePantryItemRequest(
        ingredientPublicId: '00000000-0000-4000-8000-000000000101',
        quantity: '1',
        unitCode: 'bag',
        storageLocation: PantryStorageLocation.fridge,
      );
      await controller.createItem(create);
      expect(inventoryChanges, 1);
      await controller.updateMetadata(
        testPantryId,
        const UpdatePantryMetadataRequest(
          storageLocation: PantryStorageLocation.fridge,
        ),
      );
      expect(inventoryChanges, 1);
      repository.adjustError = const ApiTransportException(
        ApiTransportFailureKind.network,
      );
      await controller.adjustItem(
        testPantryId,
        const AdjustPantryItemRequest(quantityDelta: '1'),
      );
      expect(inventoryChanges, 1);
      repository.adjustError = null;
      repository.onAdjust = (_, _) async =>
          pantryItem(quantityInitial: '10', quantityRemaining: '6');
      await controller.adjustItem(
        testPantryId,
        const AdjustPantryItemRequest(quantityDelta: '1'),
      );
      expect(inventoryChanges, 2);
      repository.onConsume = (_, _) async =>
          pantryItem(quantityInitial: '10', quantityRemaining: '5');
      await controller.consumeItem(
        testPantryId,
        const ConsumePantryItemRequest(quantity: '1'),
      );
      expect(inventoryChanges, 3);
      repository.onDiscard = (_, _) async => pantryItem(
        quantityInitial: '10',
        quantityRemaining: '0',
        status: PantryItemStatus.discarded,
      );
      await controller.discardItem(
        testPantryId,
        const DiscardPantryItemRequest(),
      );
      expect(inventoryChanges, 4);
    },
  );

  test(
    'Pantry callback invalidates only the linked shopping projection',
    () async {
      final mealRepository = FakeMealPlanningRepository();
      final meal = MealPlanningController(repository: mealRepository);
      addTearDown(meal.dispose);
      await meal.generate(generationRequest());
      await meal.loadShoppingList();
      final plan = meal.state.plan;

      final linked = PantryController(
        repository: repository,
        onInventoryChanged: meal.invalidateShoppingList,
      );
      addTearDown(linked.dispose);
      repository.items = [
        pantryItem(quantityInitial: '10', quantityRemaining: '5'),
      ];
      repository.detail = repository.items.single;
      await linked.loadInitial();
      await linked.loadDetail(testPantryId);
      await linked.createItem(
        const CreatePantryItemRequest(
          ingredientPublicId: '00000000-0000-4000-8000-000000000101',
          quantity: '1',
          unitCode: 'bag',
          storageLocation: PantryStorageLocation.fridge,
        ),
      );
      expect(meal.state.shoppingListStatus, ShoppingListLoadStatus.idle);
      expect(meal.state.plan, same(plan));
      await meal.loadShoppingList();

      await linked.updateMetadata(
        testPantryId,
        const UpdatePantryMetadataRequest(
          storageLocation: PantryStorageLocation.fridge,
        ),
      );
      expect(meal.state.shoppingListStatus, ShoppingListLoadStatus.loaded);

      repository.onAdjust = (_, _) async =>
          pantryItem(quantityInitial: '10', quantityRemaining: '6');
      await linked.adjustItem(
        testPantryId,
        const AdjustPantryItemRequest(quantityDelta: '1'),
      );
      expect(meal.state.shoppingListStatus, ShoppingListLoadStatus.idle);
      await meal.loadShoppingList();

      repository.onConsume = (_, _) async =>
          pantryItem(quantityInitial: '10', quantityRemaining: '5');
      await linked.consumeItem(
        testPantryId,
        const ConsumePantryItemRequest(quantity: '1'),
      );
      expect(meal.state.shoppingListStatus, ShoppingListLoadStatus.idle);
      await meal.loadShoppingList();

      repository.discardError = const ApiTransportException(
        ApiTransportFailureKind.network,
      );
      await linked.discardItem(testPantryId, const DiscardPantryItemRequest());
      expect(meal.state.shoppingListStatus, ShoppingListLoadStatus.loaded);
      repository.discardError = null;
      repository.onDiscard = (_, _) async => pantryItem(
        quantityInitial: '10',
        quantityRemaining: '0',
        status: PantryItemStatus.discarded,
      );
      await linked.discardItem(testPantryId, const DiscardPantryItemRequest());
      expect(meal.state.shoppingListStatus, ShoppingListLoadStatus.idle);
      expect(meal.state.plan, same(plan));
      expect(mealRepository.readIds, [planId]);
      expect(mealRepository.shoppingListReadIds, [
        planId,
        planId,
        planId,
        planId,
      ]);
    },
  );
}
