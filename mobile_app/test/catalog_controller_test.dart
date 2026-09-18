import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/application/food_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

import 'support/fake_catalog_repository.dart';

void main() {
  test('food controller loads the first page and caches categories', () async {
    final repository = FakeCatalogRepository();
    final controller = FoodCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();

    expect(controller.state.status, CatalogListStatus.loaded);
    expect(controller.state.items.single.displayName, 'Gạo nếp cái');
    expect(controller.state.categories.single.code, 'GRAINS');
    expect(repository.categoryCalls, 1);
    expect(repository.foodRequests.single.page, 0);
    expect(repository.foodRequests.single.size, 20);

    await controller.reload();

    expect(repository.categoryCalls, 1);
    expect(repository.foodRequests, hasLength(2));
  });

  test('food search and category changes reset to page zero', () async {
    final repository = FakeCatalogRepository();
    repository.onGetFoods = (request) async {
      if (request.query == 'Cà chua') {
        return _foodPage(
          page: 0,
          totalPages: 1,
          content: [testFoodTwo],
        );
      }
      return testFoodPage;
    };
    final controller = FoodCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    await controller.search('  Cà chua  ');

    expect(controller.state.searchQuery, 'Cà chua');
    expect(controller.state.page, 0);
    expect(controller.state.items.single.displayName, 'Cà chua');
    expect(repository.foodRequests.last.query, 'Cà chua');
    expect(repository.foodRequests.last.page, 0);

    await controller.setCategory(' VEGETABLES ');

    expect(controller.state.selectedCategoryCode, 'VEGETABLES');
    expect(controller.state.page, 0);
    expect(repository.foodRequests.last.categoryCode, 'VEGETABLES');
    expect(repository.foodRequests.last.page, 0);
  });

  test('food load more appends once and prevents duplicate requests', () async {
    final secondPage = Completer<FoodCatalogPage>();
    final repository = FakeCatalogRepository(
      foodPage: _foodPage(page: 0, totalPages: 2, content: [testFood]),
    );
    repository.onGetFoods = (request) {
      if (request.page == 1) {
        return secondPage.future;
      }
      return Future<FoodCatalogPage>.value(repository.foodPage);
    };
    final controller = FoodCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    final firstLoadMore = controller.loadMore();
    final duplicateLoadMore = controller.loadMore();

    expect(
      repository.foodRequests.where((request) => request.page == 1),
      hasLength(1),
    );
    expect(controller.state.status, CatalogListStatus.loadingMore);

    secondPage.complete(
      _foodPage(page: 1, totalPages: 2, content: [testFoodTwo]),
    );
    await Future.wait([firstLoadMore, duplicateLoadMore]);

    expect(controller.state.items, [testFood, testFoodTwo]);
    expect(controller.state.page, 1);
    expect(controller.state.hasMore, isFalse);
  });

  test('food load-more error preserves loaded items and can be retried', () async {
    final repository = FakeCatalogRepository(
      foodPage: _foodPage(page: 0, totalPages: 2, content: [testFood]),
    );
    repository.onGetFoods = (request) {
      if (request.page == 1) {
        return Future<FoodCatalogPage>.error(
          const ApiTransportException(ApiTransportFailureKind.network),
        );
      }
      return Future<FoodCatalogPage>.value(repository.foodPage);
    };
    final controller = FoodCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    await controller.loadMore();

    expect(controller.state.status, CatalogListStatus.loaded);
    expect(controller.state.items, [testFood]);
    expect(controller.state.loadMoreErrorMessage, isNotNull);

    repository.onGetFoods = (request) => Future<FoodCatalogPage>.value(
      _foodPage(page: 1, totalPages: 2, content: [testFoodTwo]),
    );
    await controller.loadMore();

    expect(controller.state.items, [testFood, testFoodTwo]);
    expect(controller.state.loadMoreErrorMessage, isNull);
  });

  test('older food search response cannot overwrite newer state', () async {
    final oldResponse = Completer<FoodCatalogPage>();
    final repository = FakeCatalogRepository();
    repository.onGetFoods = (request) {
      if (request.query == 'old') {
        return oldResponse.future;
      }
      if (request.query == 'new') {
        return Future<FoodCatalogPage>.value(
          _foodPage(page: 0, totalPages: 1, content: [testFoodTwo]),
        );
      }
      return Future<FoodCatalogPage>.value(testFoodPage);
    };
    final controller = FoodCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    final oldSearch = controller.search('old');
    final newSearch = controller.search('new');
    await newSearch;
    oldResponse.complete(
      _foodPage(page: 0, totalPages: 1, content: [testFood]),
    );
    await oldSearch;

    expect(controller.state.searchQuery, 'new');
    expect(controller.state.items.single.displayName, 'Cà chua');
  });

  test('food initial error can be retried without stale rows', () async {
    final repository = FakeCatalogRepository(
      foodsError: const ApiTransportException(ApiTransportFailureKind.network),
    );
    final controller = FoodCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    expect(controller.state.status, CatalogListStatus.error);
    expect(controller.state.items, isEmpty);

    repository.foodsError = null;
    await controller.reload();

    expect(controller.state.status, CatalogListStatus.loaded);
    expect(controller.state.items, [testFood]);
  });

  test('ingredient controller uses the same paged search contract', () async {
    final repository = FakeCatalogRepository();
    final controller = IngredientCatalogController(repository: repository);
    addTearDown(controller.dispose);

    await controller.loadInitial();
    await controller.search('  Gạo nếp cái  ');

    expect(controller.state.status, CatalogListStatus.loaded);
    expect(controller.state.searchQuery, 'Gạo nếp cái');
    expect(controller.state.items.single.code, 'ING_SMILING_VN_1001');
    expect(repository.ingredientRequests.last.query, 'Gạo nếp cái');
    expect(repository.ingredientRequests.last.page, 0);
  });
}

FoodCatalogPage _foodPage({
  required int page,
  required int totalPages,
  required List<FoodCatalogItem> content,
}) => FoodCatalogPage(
  page: page,
  size: 20,
  totalElements: content.length,
  totalPages: totalPages,
  content: content,
);
