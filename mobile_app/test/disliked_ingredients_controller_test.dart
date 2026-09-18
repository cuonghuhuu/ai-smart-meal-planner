import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';

import 'support/fake_catalog_repository.dart';
import 'support/fake_disliked_ingredients.dart';

void main() {
  test('load, add, edit, remove, and dirty state are strongly typed', () async {
    final controller = _controller(
      repository: FakeDislikedIngredientsRepository(preferences: const []),
    );
    addTearDown(controller.dispose);

    await controller.load();
    expect(controller.state.status, DislikedIngredientsStatus.loaded);
    expect(controller.state.hasChanges, isFalse);

    controller.addIngredient(testIngredient);
    controller.addIngredient(testIngredient);
    expect(controller.state.draftPreferences, hasLength(1));
    expect(
      controller.state.draftPreferences.single.strength,
      DislikedIngredientStrength.dislike,
    );
    expect(controller.state.hasChanges, isTrue);

    controller.setStrength(
      testIngredientId,
      DislikedIngredientStrength.avoid,
    );
    controller.setNote(testIngredientId, '  Không hợp mùi  ');
    expect(
      controller.state.draftPreferences.single.strength,
      DislikedIngredientStrength.avoid,
    );
    expect(
      controller.state.draftPreferences.single.note,
      '  Không hợp mùi  ',
    );

    controller.removeIngredient(testIngredientId);
    expect(controller.state.draftPreferences, isEmpty);
    expect(controller.state.hasChanges, isFalse);
  });

  test('save sends the entire draft and adopts the replacement response', () async {
    final existing = _preference(testIngredientTwo);
    final repository = FakeDislikedIngredientsRepository(
      preferences: [existing],
    );
    final controller = _controller(repository: repository);
    addTearDown(controller.dispose);

    await controller.load();
    controller.addIngredient(testIngredient);
    await controller.save();

    expect(repository.saveCalls, hasLength(1));
    expect(repository.saveCalls.single, hasLength(2));
    expect(
      repository.saveCalls.single.map((selection) => selection.ingredientPublicId),
      containsAll([testIngredientId, testIngredientIdTwo]),
    );
    expect(controller.state.hasChanges, isFalse);
    expect(controller.state.savedPreferences, hasLength(2));
    expect(controller.state.saveMessage, isNotNull);
  });

  test('save failure preserves the complete local draft', () async {
    final repository = FakeDislikedIngredientsRepository(
      preferences: const [],
      saveError: const ApiTransportException(ApiTransportFailureKind.network),
    );
    final controller = _controller(repository: repository);
    addTearDown(controller.dispose);

    await controller.load();
    controller.addIngredient(testIngredient);
    controller.setNote(testIngredientId, 'Keep this edit');
    await controller.save();

    expect(controller.state.status, DislikedIngredientsStatus.error);
    expect(controller.state.draftPreferences, hasLength(1));
    expect(controller.state.draftPreferences.single.note, 'Keep this edit');
    expect(controller.state.hasChanges, isTrue);
  });

  test('note validation blocks a replacement request', () async {
    final repository = FakeDislikedIngredientsRepository(preferences: const []);
    final controller = _controller(repository: repository);
    addTearDown(controller.dispose);

    await controller.load();
    controller.addIngredient(testIngredient);
    controller.setNote(testIngredientId, 'x' * 256);
    await controller.save();

    expect(repository.saveCalls, isEmpty);
    expect(controller.state.status, DislikedIngredientsStatus.error);
    expect(controller.state.hasChanges, isTrue);
  });

  test('picker searches page zero and appends a later page once', () async {
    final repository = FakeCatalogRepository();
    final pageOne = IngredientCatalogPage(
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 2,
      content: [testIngredient],
    );
    final pageTwo = IngredientCatalogPage(
      page: 1,
      size: 20,
      totalElements: 2,
      totalPages: 2,
      content: [testIngredientTwo],
    );
    repository.onGetIngredients = (request) => Future.value(
      request.page == 0 ? pageOne : pageTwo,
    );
    final controller = _controller(
      repository: FakeDislikedIngredientsRepository(preferences: const []),
      catalogRepository: repository,
    );
    addTearDown(controller.dispose);

    await controller.load();
    await controller.searchPicker('  Gạo  ');
    expect(controller.pickerState.query, 'Gạo');
    expect(repository.ingredientRequests.single.query, 'Gạo');
    expect(repository.ingredientRequests.single.page, 0);

    final firstLoadMore = controller.loadPickerMore();
    final duplicateLoadMore = controller.loadPickerMore();
    await Future.wait([firstLoadMore, duplicateLoadMore]);

    expect(
      repository.ingredientRequests.where((request) => request.page == 1),
      hasLength(1),
    );
    expect(controller.pickerState.items, hasLength(2));
  });

  test('stale picker response cannot overwrite a newer query', () async {
    final oldResponse = Completer<IngredientCatalogPage>();
    final newResponse = Completer<IngredientCatalogPage>();
    final repository = FakeCatalogRepository();
    repository.onGetIngredients = (request) =>
        request.query == 'old' ? oldResponse.future : newResponse.future;
    final controller = _controller(
      repository: FakeDislikedIngredientsRepository(preferences: const []),
      catalogRepository: repository,
    );
    addTearDown(controller.dispose);

    final oldLoad = controller.searchPicker('old');
    final newLoad = controller.searchPicker('new');
    newResponse.complete(
      IngredientCatalogPage(
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        content: [testIngredientTwo],
      ),
    );
    await newLoad;
    oldResponse.complete(
      IngredientCatalogPage(
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        content: [testIngredient],
      ),
    );
    await oldLoad;

    expect(controller.pickerState.query, 'new');
    expect(controller.pickerState.items.single.publicId, testIngredientIdTwo);
  });

  test('picker load-more failure preserves existing items', () async {
    final repository = FakeCatalogRepository();
    repository.onGetIngredients = (request) {
      if (request.page == 1) {
        return Future<IngredientCatalogPage>.error(
          const ApiTransportException(ApiTransportFailureKind.network),
        );
      }
      return Future.value(
        IngredientCatalogPage(
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 2,
          content: [testIngredient],
        ),
      );
    };
    final controller = _controller(
      repository: FakeDislikedIngredientsRepository(preferences: const []),
      catalogRepository: repository,
    );
    addTearDown(controller.dispose);

    await controller.searchPicker('');
    await controller.loadPickerMore();

    expect(controller.pickerState.items, hasLength(1));
    expect(controller.pickerState.loadMoreErrorMessage, isNotNull);
    expect(controller.pickerState.status, DislikedIngredientPickerStatus.loaded);
  });

  test('session reset clears private state and invalidates in-flight loads', () async {
    final preferenceResponse = Completer<List<DislikedIngredientPreference>>();
    final pickerResponse = Completer<IngredientCatalogPage>();
    final preferenceRepository = FakeDislikedIngredientsRepository(
      preferences: const [],
    )..onGet = () => preferenceResponse.future;
    final catalogRepository = FakeCatalogRepository()
      ..onGetIngredients = (_) => pickerResponse.future;
    final controller = _controller(
      repository: preferenceRepository,
      catalogRepository: catalogRepository,
    );
    addTearDown(controller.dispose);

    final preferenceLoad = controller.load();
    final pickerLoad = controller.searchPicker('private');
    controller.resetForSessionChange();
    preferenceResponse.complete([_preference(testIngredient)]);
    pickerResponse.complete(testIngredientPage);
    await Future.wait([preferenceLoad, pickerLoad]);

    expect(controller.state.status, DislikedIngredientsStatus.initial);
    expect(controller.state.savedPreferences, isEmpty);
    expect(controller.state.draftPreferences, isEmpty);
    expect(controller.pickerState.items, isEmpty);
    expect(controller.pickerState.query, isEmpty);
  });
}

DislikedIngredientsController _controller({
  required FakeDislikedIngredientsRepository repository,
  FakeCatalogRepository? catalogRepository,
}) => DislikedIngredientsController(
  repository: repository,
  catalogRepository: catalogRepository ?? FakeCatalogRepository(),
);

const testIngredientIdTwo = '00000000-0000-4000-8000-000000000102';

const testIngredientTwo = IngredientCatalogItem(
  publicId: testIngredientIdTwo,
  code: 'ING_SMILING_VN_1002',
  displayName: 'Cà chua',
  category: testCategory,
  staple: false,
);

DislikedIngredientPreference _preference(IngredientCatalogItem ingredient) =>
    DislikedIngredientPreference(
      ingredientPublicId: ingredient.publicId,
      ingredientCode: ingredient.code,
      ingredientDisplayName: ingredient.displayName,
      category: ingredient.category,
      strength: DislikedIngredientStrength.avoid,
      note: null,
    );
