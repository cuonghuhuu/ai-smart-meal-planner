import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_pantry_repository.dart';

void main() {
  late FakePantryRepository repository;
  late PantryController controller;

  setUp(() {
    repository = FakePantryRepository();
    controller = PantryController(repository: repository);
  });
  tearDown(() => controller.dispose());

  test('initial state and first open-list load', () async {
    expect(controller.listState.status, PantryLoadStatus.initial);
    expect(controller.listState.includeClosed, isFalse);
    expect(controller.detailState.status, PantryLoadStatus.initial);
    final pending = Completer<List<PantryItem>>();
    repository.onList = (_) => pending.future;
    final load = controller.loadInitial();
    expect(controller.listState.status, PantryLoadStatus.loading);
    expect(repository.listRequests, [false]);
    pending.complete([pantryItem()]);
    await load;
    expect(controller.listState.status, PantryLoadStatus.loaded);
    expect(controller.listState.items.single.publicId, testPantryId);
    await controller.loadInitial();
    expect(repository.listRequests, [false]);
  });

  test('empty, error, retry, and refresh', () async {
    repository.items = [];
    await controller.loadInitial();
    expect(controller.listState.status, PantryLoadStatus.loaded);
    expect(controller.listState.items, isEmpty);
    repository.listError = const ApiTransportException(
      ApiTransportFailureKind.network,
    );
    await controller.refreshList();
    expect(controller.listState.status, PantryLoadStatus.error);
    expect(controller.listState.errorMessage, AppStrings.unableToReachService);
    repository.listError = null;
    repository.items = [pantryItem()];
    await controller.retryList();
    expect(controller.listState.items, hasLength(1));
    expect(repository.listRequests, [false, false, false]);
  });

  test('switching includeClosed makes distinct reads both ways', () async {
    await controller.loadInitial();
    await controller.setIncludeClosed(true);
    expect(controller.listState.includeClosed, isTrue);
    await controller.setIncludeClosed(false);
    expect(controller.listState.includeClosed, isFalse);
    expect(repository.listRequests, [false, true, false]);
  });

  test('older list result cannot replace newer mode', () async {
    final first = Completer<List<PantryItem>>();
    final second = Completer<List<PantryItem>>();
    repository.onList = (closed) => closed ? second.future : first.future;
    final open = controller.loadInitial();
    final history = controller.setIncludeClosed(true);
    second.complete([pantryItem(publicId: testPantryIdTwo)]);
    await history;
    first.complete([pantryItem()]);
    await open;
    expect(controller.listState.includeClosed, isTrue);
    expect(controller.listState.items.single.publicId, testPantryIdTwo);
  });

  test(
    'session reset clears list and ignores pending old-account read',
    () async {
      final pending = Completer<List<PantryItem>>();
      repository.onList = (_) => pending.future;
      final oldRead = controller.loadInitial();
      controller.resetForSessionChange();
      expect(controller.listState.status, PantryLoadStatus.initial);
      expect(controller.listState.items, isEmpty);
      repository.onList = (_) async => [pantryItem(publicId: testPantryIdTwo)];
      await controller.loadInitial();
      pending.complete([pantryItem()]);
      await oldRead;
      expect(controller.listState.items.single.publicId, testPantryIdTwo);
      controller.resetForSessionChange();
      expect(controller.listState.items, isEmpty);
    },
  );

  test('detail load, not-found error, and retry', () async {
    await controller.loadDetail(testPantryId);
    expect(controller.detailState.status, PantryLoadStatus.loaded);
    expect(controller.detailState.item?.publicId, testPantryId);
    repository.detailError = const ApiHttpException(404);
    await controller.loadDetail(testPantryId);
    expect(controller.detailState.notFound, isTrue);
    expect(controller.detailState.errorMessage, AppStrings.pantryNotFound);
    repository.detailError = null;
    await controller.retryDetail();
    expect(controller.detailState.status, PantryLoadStatus.loaded);
    controller.resetForSessionChange();
    expect(controller.detailState.status, PantryLoadStatus.initial);
    expect(controller.detailState.item, isNull);
  });

  test('detail A cannot overwrite B, including errors', () async {
    final first = Completer<PantryItem>();
    final second = Completer<PantryItem>();
    repository.onDetail = (id) =>
        id == testPantryId ? first.future : second.future;
    final a = controller.loadDetail(testPantryId);
    final b = controller.loadDetail(testPantryIdTwo);
    second.complete(pantryItem(publicId: testPantryIdTwo));
    await b;
    first.completeError(const ApiHttpException(404));
    await a;
    expect(controller.detailState.item?.publicId, testPantryIdTwo);
    expect(controller.detailState.notFound, isFalse);
  });

  test('reset clears detail and ignores pending response', () async {
    final pending = Completer<PantryItem>();
    repository.onDetail = (_) => pending.future;
    final read = controller.loadDetail(testPantryId);
    expect(controller.detailState.status, PantryLoadStatus.loading);
    controller.resetForSessionChange();
    expect(controller.detailState.status, PantryLoadStatus.initial);
    expect(controller.detailState.item, isNull);
    pending.complete(pantryItem());
    await read;
    expect(controller.detailState.item, isNull);
  });

  group('create', () {
    const request = CreatePantryItemRequest(
      ingredientPublicId: '00000000-0000-4000-8000-000000000101',
      quantity: '1.2345',
      unitCode: 'bag',
      storageLocation: PantryStorageLocation.fridge,
    );

    test('adopts response as a distinct lot in the current list', () async {
      await controller.loadInitial();
      final result = await controller.createItem(request);
      expect(result?.publicId, testPantryIdTwo);
      expect(controller.mutationState.status, PantryMutationStatus.succeeded);
      expect(controller.listState.items.map((item) => item.publicId).toSet(), {
        testPantryId,
        testPantryIdTwo,
      });
      expect(repository.createRequests, [request]);
    });

    test('failure leaves list unchanged and supports retry', () async {
      await controller.loadInitial();
      repository.createError = const ApiHttpException(
        400,
        problem: ApiProblem(status: 400, code: 'UNIT_NOT_FOUND', detail: null),
      );
      expect(await controller.createItem(request), isNull);
      expect(
        controller.mutationState.errorMessage,
        AppStrings.pantryUnitNotFound,
      );
      expect(controller.listState.items.single.publicId, testPantryId);
      repository.createError = null;
      expect((await controller.createItem(request))?.publicId, testPantryIdTwo);
    });

    test('duplicate submission is blocked while pending', () async {
      final pending = Completer<PantryItem>();
      repository.onCreate = (_) => pending.future;
      final first = controller.createItem(request);
      expect(controller.mutationState.status, PantryMutationStatus.submitting);
      expect(await controller.createItem(request), isNull);
      expect(repository.createRequests, hasLength(1));
      pending.complete(pantryItem(publicId: testPantryIdTwo));
      await first;
    });

    test('old list refresh cannot erase successful create', () async {
      await controller.loadInitial();
      final pending = Completer<List<PantryItem>>();
      repository.onList = (_) => pending.future;
      final refresh = controller.refreshList();
      await controller.createItem(request);
      pending.complete([pantryItem()]);
      await refresh;
      expect(
        controller.listState.items.map((item) => item.publicId),
        contains(testPantryIdTwo),
      );
    });

    test(
      'create during history-mode load triggers a fresh history read',
      () async {
        await controller.loadInitial();
        final pendingHistory = Completer<List<PantryItem>>();
        repository.onList = (includeClosed) => includeClosed
            ? pendingHistory.future
            : Future.value(repository.items);
        final oldHistoryRead = controller.setIncludeClosed(true);
        expect(controller.listState.items, isEmpty);
        await controller.createItem(request);
        expect(controller.listState.status, PantryLoadStatus.initial);
        expect(controller.listState.includeClosed, isTrue);

        repository.onList = (_) async => [
          pantryItem(),
          pantryItem(publicId: testPantryIdTwo),
        ];
        await controller.loadInitial();
        pendingHistory.complete([pantryItem()]);
        await oldHistoryRead;
        expect(controller.listState.includeClosed, isTrue);
        expect(
          controller.listState.items.map((item) => item.publicId),
          contains(testPantryIdTwo),
        );
      },
    );

    test('logout or account reset invalidates pending create', () async {
      final pending = Completer<PantryItem>();
      repository.onCreate = (_) => pending.future;
      final oldCreate = controller.createItem(request);
      controller.resetForSessionChange();
      repository.items = [pantryItem(publicId: testPantryIdTwo)];
      await controller.loadInitial();
      pending.complete(pantryItem());
      expect(await oldCreate, isNull);
      expect(controller.listState.items.single.publicId, testPantryIdTwo);
      expect(controller.mutationState.status, PantryMutationStatus.idle);
    });
  });

  group('metadata update', () {
    const request = UpdatePantryMetadataRequest(
      storageLocation: PantryStorageLocation.freezer,
      acquiredOn: null,
      expiryDate: null,
      note: null,
    );

    test('returned item replaces detail and list row', () async {
      await controller.loadInitial();
      await controller.loadDetail(testPantryId);
      repository.onUpdateMetadata = (_, _) async => pantryItem(
        storageLocation: PantryStorageLocation.freezer,
        note: null,
      );
      final item = await controller.updateMetadata(testPantryId, request);
      expect(item?.storageLocation, PantryStorageLocation.freezer);
      expect(controller.detailState.item, same(item));
      expect(controller.listState.items.single, same(item));
      expect(repository.updateRequests.single.$2.toJson(), {
        'storageLocation': 'FREEZER',
        'acquiredOn': null,
        'expiryDate': null,
        'expiryKind': 'UNKNOWN',
        'expiryConfidence': 'UNKNOWN',
        'note': null,
      });
    });

    test('failure retains prior detail and reports safe message', () async {
      await controller.loadDetail(testPantryId);
      repository.updateError = const ApiHttpException(
        409,
        problem: ApiProblem(status: 409, code: 'CONFLICT', detail: 'private'),
      );
      expect(await controller.updateMetadata(testPantryId, request), isNull);
      expect(controller.detailState.item?.publicId, testPantryId);
      expect(controller.mutationState.errorMessage, AppStrings.pantryConflict);
    });

    test('detail switch invalidates update for prior lot', () async {
      await controller.loadDetail(testPantryId);
      final pending = Completer<PantryItem>();
      repository.onUpdateMetadata = (_, _) => pending.future;
      final update = controller.updateMetadata(testPantryId, request);
      repository.detail = pantryItem(publicId: testPantryIdTwo);
      await controller.loadDetail(testPantryIdTwo);
      pending.complete(
        pantryItem(storageLocation: PantryStorageLocation.freezer),
      );
      expect(await update, isNull);
      expect(controller.detailState.item?.publicId, testPantryIdTwo);
    });

    test('session reset invalidates pending update', () async {
      await controller.loadDetail(testPantryId);
      final pending = Completer<PantryItem>();
      repository.onUpdateMetadata = (_, _) => pending.future;
      final update = controller.updateMetadata(testPantryId, request);
      controller.resetForSessionChange();
      pending.complete(
        pantryItem(storageLocation: PantryStorageLocation.freezer),
      );
      expect(await update, isNull);
      expect(controller.detailState.item, isNull);
      expect(controller.mutationState.status, PantryMutationStatus.idle);
    });

    test('old list refresh cannot erase updated row', () async {
      await controller.loadInitial();
      await controller.loadDetail(testPantryId);
      final pending = Completer<List<PantryItem>>();
      repository.onList = (_) => pending.future;
      final refresh = controller.refreshList();
      repository.onUpdateMetadata = (_, _) async =>
          pantryItem(storageLocation: PantryStorageLocation.freezer);
      await controller.updateMetadata(testPantryId, request);
      pending.complete([pantryItem()]);
      await refresh;
      expect(
        controller.listState.items.single.storageLocation,
        PantryStorageLocation.freezer,
      );
    });
  });
}
