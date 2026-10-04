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
}
