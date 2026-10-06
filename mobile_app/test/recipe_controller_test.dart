import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_state.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

const _idA = '00000000-0000-4000-8000-000000000001';
const _idB = '00000000-0000-4000-8000-000000000002';
const _tag = RecipeTag(
  code: 'VEGAN',
  displayName: 'Vegan',
  tagKind: RecipeTagKind.diet,
);
const _slot = RecipeMealSlot(
  code: 'DINNER',
  displayName: 'Dinner',
  displayOrder: 5,
  typicalTime: '18:00:00',
  mainMeal: true,
);
const _itemA = RecipeItem(
  publicId: _idA,
  title: 'A',
  summary: null,
  servings: 2,
  prepMinutes: null,
  cookMinutes: 10,
  totalMinutes: 10,
  difficulty: RecipeDifficulty.easy,
  imageUrl: null,
  source: RecipeSource.curated,
  tags: [_tag],
  mealSlots: [_slot],
);
const _itemB = RecipeItem(
  publicId: _idB,
  title: 'B',
  summary: null,
  servings: 2,
  prepMinutes: null,
  cookMinutes: 15,
  totalMinutes: 15,
  difficulty: RecipeDifficulty.easy,
  imageUrl: null,
  source: RecipeSource.curated,
  tags: [],
  mealSlots: [],
);

RecipePage _page(int page, List<RecipeItem> items, {int totalPages = 1}) =>
    RecipePage(
      page: page,
      size: 20,
      totalElements: totalPages == 1 ? items.length : 2,
      totalPages: totalPages,
      content: items,
    );

RecipeDetail _detail(String id) => RecipeDetail(
  publicId: id,
  title: id == _idA ? 'A' : 'B',
  slug: 'recipe',
  summary: null,
  servings: 2,
  prepMinutes: null,
  cookMinutes: 10,
  totalMinutes: 10,
  difficulty: RecipeDifficulty.easy,
  instructionsNote: null,
  imageUrl: null,
  source: RecipeSource.curated,
  sourceReference: null,
  status: RecipeStatus.published,
  publishedAt: '2026-01-01T10:00:00',
  ingredients: const [],
  steps: const [],
  tags: const [],
  mealSlots: const [],
  nutrition: null,
);

typedef _Request = ({
  String? query,
  String? mealSlotCode,
  String? tagCode,
  int? maxMinutes,
  int page,
  int size,
});

final class _FakeRecipeRepository implements RecipeRepository {
  final requests = <_Request>[];
  final detailIds = <String>[];
  int tagCalls = 0;
  int slotCalls = 0;
  RecipePage pageResult = _page(0, [_itemA]);
  Future<RecipePage> Function(_Request)? onRecipes;
  Future<RecipeDetail> Function(String)? onDetail;
  Future<List<RecipeTag>> Function()? onTags;
  Future<List<RecipeMealSlot>> Function()? onSlots;

  @override
  Future<RecipePage> getRecipes({
    String? query,
    String? mealSlotCode,
    String? tagCode,
    int? maxMinutes,
    int page = 0,
    int size = 20,
  }) {
    final request = (
      query: query,
      mealSlotCode: mealSlotCode,
      tagCode: tagCode,
      maxMinutes: maxMinutes,
      page: page,
      size: size,
    );
    requests.add(request);
    return onRecipes?.call(request) ?? Future.value(pageResult);
  }

  @override
  Future<RecipeDetail> getRecipe(String publicId) {
    detailIds.add(publicId);
    return onDetail?.call(publicId) ?? Future.value(_detail(publicId));
  }

  @override
  Future<List<RecipeTag>> getRecipeTags() {
    tagCalls++;
    return onTags?.call() ?? Future.value([_tag]);
  }

  @override
  Future<List<RecipeMealSlot>> getMealSlotTypes() {
    slotCalls++;
    return onSlots?.call() ?? Future.value([_slot]);
  }
}

void main() {
  late _FakeRecipeRepository repository;
  late RecipeController controller;

  setUp(() {
    repository = _FakeRecipeRepository();
    controller = RecipeController(repository: repository);
  });
  tearDown(() => controller.dispose());

  test(
    'initial load exposes server page and avoids duplicate initial read',
    () async {
      await controller.loadInitial();
      await controller.loadInitial();
      expect(controller.catalogState.status, RecipeListStatus.loaded);
      expect(controller.catalogState.items, [_itemA]);
      expect(controller.catalogState.page, 0);
      expect(controller.catalogState.size, 20);
      expect(repository.requests, hasLength(1));
    },
  );

  test('initial error has safe message and retry loads fresh data', () async {
    repository.onRecipes = (_) =>
        Future.error(Exception('private server text'));
    await controller.loadInitial();
    expect(controller.catalogState.status, RecipeListStatus.error);
    expect(controller.catalogState.errorMessage, AppStrings.catalogLoadFailed);
    expect(controller.catalogState.items, isEmpty);
    repository.onRecipes = (_) async => _page(0, [_itemB]);
    await controller.retryCatalog();
    expect(controller.catalogState.status, RecipeListStatus.loaded);
    expect(controller.catalogState.items, [_itemB]);
  });

  test('search trims text and blank query clears server search', () async {
    await controller.search('  soup & rice  ');
    expect(controller.catalogState.filters.query, 'soup & rice');
    expect(repository.requests.last.query, 'soup & rice');
    await controller.search('  ');
    expect(controller.catalogState.filters.query, '');
    expect(repository.requests.last.query, '');
    expect(repository.requests.last.page, 0);
  });

  test(
    'meal slot, tag, time, and combined filters restart page zero',
    () async {
      await controller.setMealSlotCode(' DINNER ');
      expect(repository.requests.last.mealSlotCode, 'DINNER');
      await controller.setTagCode(' VEGAN ');
      expect(repository.requests.last.tagCode, 'VEGAN');
      await controller.setMaxMinutes(30);
      expect(repository.requests.last.maxMinutes, 30);
      await controller.search(' soup ');
      expect(repository.requests.last, (
        query: 'soup',
        mealSlotCode: 'DINNER',
        tagCode: 'VEGAN',
        maxMinutes: 30,
        page: 0,
        size: 20,
      ));
      await controller.setMealSlotCode(' ');
      await controller.setTagCode(null);
      expect(controller.catalogState.filters.mealSlotCode, isNull);
      expect(controller.catalogState.filters.tagCode, isNull);
      await controller.clearFilters();
      expect(controller.catalogState.filters.query, '');
      expect(controller.catalogState.filters.maxMinutes, isNull);
      expect(repository.requests.last.page, 0);
    },
  );

  test('changing a filter after page one restarts at page zero', () async {
    repository.onRecipes = (request) async => request.page == 1
        ? _page(1, [_itemB], totalPages: 2)
        : _page(0, [_itemA], totalPages: 2);
    await controller.loadInitial();
    await controller.loadMore();
    expect(controller.catalogState.page, 1);
    await controller.setTagCode('VEGAN');
    expect(repository.requests.last.page, 0);
    expect(controller.catalogState.page, 0);
    expect(controller.catalogState.items, [_itemA]);
  });

  test('invalid max-minutes leaves filters and requests unchanged', () async {
    expect(() => controller.setMaxMinutes(10081), throwsArgumentError);
    expect(repository.requests, isEmpty);
    expect(controller.catalogState.filters.maxMinutes, isNull);
  });

  test(
    'load more appends, guards duplicate requests, and stops at last page',
    () async {
      repository.pageResult = _page(0, [_itemA], totalPages: 2);
      final next = Completer<RecipePage>();
      repository.onRecipes = (request) =>
          request.page == 1 ? next.future : Future.value(repository.pageResult);
      await controller.loadInitial();
      final first = controller.loadMore();
      final duplicate = controller.loadMore();
      expect(controller.catalogState.status, RecipeListStatus.loadingMore);
      expect(
        repository.requests.where((request) => request.page == 1),
        hasLength(1),
      );
      next.complete(_page(1, [_itemB], totalPages: 2));
      await Future.wait([first, duplicate]);
      expect(controller.catalogState.items, [_itemA, _itemB]);
      expect(controller.catalogState.page, 1);
      expect(controller.catalogState.hasMore, isFalse);
      await controller.loadMore();
      expect(repository.requests, hasLength(2));
    },
  );

  test('load-more error keeps rows and can retry the same page', () async {
    repository.pageResult = _page(0, [_itemA], totalPages: 2);
    repository.onRecipes = (request) => request.page == 1
        ? Future.error(
            const ApiTransportException(ApiTransportFailureKind.network),
          )
        : Future.value(repository.pageResult);
    await controller.loadInitial();
    await controller.loadMore();
    expect(controller.catalogState.status, RecipeListStatus.loaded);
    expect(controller.catalogState.items, [_itemA]);
    expect(controller.catalogState.page, 0);
    expect(
      controller.catalogState.loadMoreErrorMessage,
      AppStrings.unableToReachService,
    );
    repository.onRecipes = (request) async => request.page == 1
        ? _page(1, [_itemB], totalPages: 2)
        : repository.pageResult;
    await controller.loadMore();
    expect(controller.catalogState.items, [_itemA, _itemB]);
    expect(controller.catalogState.loadMoreErrorMessage, isNull);
  });

  test('older search cannot overwrite newer search', () async {
    final old = Completer<RecipePage>();
    repository.onRecipes = (request) =>
        request.query == 'old' ? old.future : Future.value(_page(0, [_itemB]));
    final oldCall = controller.search('old');
    await controller.search('new');
    old.complete(_page(0, [_itemA]));
    await oldCall;
    expect(controller.catalogState.filters.query, 'new');
    expect(controller.catalogState.items, [_itemB]);
  });

  test('older filter failure cannot replace newer combined filters', () async {
    final old = Completer<RecipePage>();
    repository.onRecipes = (request) => request.tagCode == 'OLD'
        ? old.future
        : Future.value(_page(0, [_itemB]));
    final oldCall = controller.setTagCode('OLD');
    await controller.setTagCode('VEGAN');
    old.completeError(const ApiHttpException(500));
    await oldCall;
    expect(controller.catalogState.filters.tagCode, 'VEGAN');
    expect(controller.catalogState.items, [_itemB]);
  });

  test(
    'old load-more page cannot append into a new search generation',
    () async {
      final oldPage = Completer<RecipePage>();
      repository.onRecipes = (request) {
        if (request.page == 1) return oldPage.future;
        return Future.value(
          request.query == 'new'
              ? _page(0, [_itemB])
              : _page(0, [_itemA], totalPages: 2),
        );
      };
      await controller.loadInitial();
      final oldLoadMore = controller.loadMore();
      await controller.search('new');
      oldPage.complete(_page(1, [_itemA], totalPages: 2));
      await oldLoadMore;
      expect(controller.catalogState.items, [_itemB]);
      expect(controller.catalogState.page, 0);
    },
  );

  test(
    'session reset clears filters/list and rejects pending old-account result',
    () async {
      final old = Completer<RecipePage>();
      repository.onRecipes = (request) => request.query == 'old'
          ? old.future
          : Future.value(_page(0, [_itemB]));
      final oldCall = controller.search('old');
      controller.resetForSessionChange();
      expect(controller.catalogState.status, RecipeListStatus.initial);
      expect(controller.catalogState.filters.query, '');
      expect(controller.catalogState.items, isEmpty);
      await controller.loadInitial();
      old.complete(_page(0, [_itemA]));
      await oldCall;
      expect(controller.catalogState.items, [_itemB]);
    },
  );

  test(
    'detail loads, fails safely, and retries independently of catalog',
    () async {
      await controller.loadInitial();
      await controller.loadDetail(_idA);
      expect(controller.detailState.status, RecipeDetailStatus.loaded);
      expect(controller.detailState.recipe?.publicId, _idA);
      repository.onDetail = (_) => Future.error(const ApiHttpException(404));
      await controller.loadDetail(_idA);
      expect(controller.detailState.status, RecipeDetailStatus.error);
      expect(controller.detailState.errorMessage, AppStrings.requestFailed);
      expect(controller.catalogState.items, [_itemA]);
      repository.onDetail = null;
      await controller.retryDetail();
      expect(controller.detailState.recipe?.publicId, _idA);
    },
  );

  test('late detail A cannot overwrite detail B', () async {
    final old = Completer<RecipeDetail>();
    repository.onDetail = (id) =>
        id == _idA ? old.future : Future.value(_detail(_idB));
    final first = controller.loadDetail(_idA);
    await controller.loadDetail(_idB);
    old.complete(_detail(_idA));
    await first;
    expect(controller.detailState.recipe?.publicId, _idB);
  });

  test('reset clears detail and ignores its pending response', () async {
    final old = Completer<RecipeDetail>();
    repository.onDetail = (_) => old.future;
    final first = controller.loadDetail(_idA);
    controller.resetForSessionChange();
    old.complete(_detail(_idA));
    await first;
    expect(controller.detailState.status, RecipeDetailStatus.initial);
    expect(controller.detailState.recipe, isNull);
  });

  test('tags and meal slots load independently of catalog', () async {
    await controller.loadInitial();
    await controller.loadReferences();
    expect(controller.tagsState.items, [_tag]);
    expect(controller.mealSlotsState.items, [_slot]);
    expect(controller.tagsState.status, RecipeReferenceStatus.loaded);
    expect(controller.mealSlotsState.status, RecipeReferenceStatus.loaded);
    expect(controller.catalogState.items, [_itemA]);
  });

  test(
    'reference failures preserve loaded data and each retry is independent',
    () async {
      await controller.loadReferences();
      repository.onTags = () => Future.error(Exception('private tag error'));
      repository.onSlots = () => Future.error(const ApiHttpException(500));
      await controller.loadReferences();
      expect(controller.tagsState.status, RecipeReferenceStatus.error);
      expect(controller.tagsState.items, [_tag]);
      expect(controller.tagsState.errorMessage, AppStrings.catalogLoadFailed);
      expect(controller.mealSlotsState.status, RecipeReferenceStatus.error);
      expect(controller.mealSlotsState.items, [_slot]);
      expect(
        controller.mealSlotsState.errorMessage,
        AppStrings.serviceUnavailable,
      );
      repository.onTags = null;
      await controller.retryRecipeTags();
      expect(controller.tagsState.status, RecipeReferenceStatus.loaded);
      expect(controller.mealSlotsState.status, RecipeReferenceStatus.error);
      repository.onSlots = null;
      await controller.retryMealSlotTypes();
      expect(controller.mealSlotsState.status, RecipeReferenceStatus.loaded);
    },
  );

  test(
    'older reference responses cannot overwrite newer loads or session',
    () async {
      final oldTags = Completer<List<RecipeTag>>();
      final oldSlots = Completer<List<RecipeMealSlot>>();
      repository.onTags = () => oldTags.future;
      repository.onSlots = () => oldSlots.future;
      final first = controller.loadReferences();
      repository.onTags = () async => [];
      repository.onSlots = () async => [];
      await controller.loadReferences();
      oldTags.complete([_tag]);
      oldSlots.complete([_slot]);
      await first;
      expect(controller.tagsState.items, isEmpty);
      expect(controller.mealSlotsState.items, isEmpty);

      final sessionTags = Completer<List<RecipeTag>>();
      final sessionSlots = Completer<List<RecipeMealSlot>>();
      repository.onTags = () => sessionTags.future;
      repository.onSlots = () => sessionSlots.future;
      final oldSession = controller.loadReferences();
      controller.resetForSessionChange();
      sessionTags.complete([_tag]);
      sessionSlots.complete([_slot]);
      await oldSession;
      expect(controller.tagsState.status, RecipeReferenceStatus.initial);
      expect(controller.mealSlotsState.status, RecipeReferenceStatus.initial);
      expect(controller.tagsState.items, isEmpty);
      expect(controller.mealSlotsState.items, isEmpty);
    },
  );
}
