import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_browse_page.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

const _tag = RecipeTag(
  code: 'VEGAN',
  displayName: 'Vegan',
  tagKind: RecipeTagKind.diet,
);
const _slot = RecipeMealSlot(
  code: 'DINNER',
  displayName: 'Dinner',
  displayOrder: 1,
  typicalTime: null,
  mainMeal: true,
);
const _first = RecipeItem(
  publicId: 'recipe-a',
  title: 'Rice bowl',
  summary: 'Fresh and quick',
  servings: 2,
  prepMinutes: 5,
  cookMinutes: 10,
  totalMinutes: 15,
  difficulty: RecipeDifficulty.easy,
  imageUrl: null,
  source: RecipeSource.curated,
  tags: [_tag],
  mealSlots: [_slot],
);
const _second = RecipeItem(
  publicId: 'recipe-b',
  title: 'Noodle soup',
  summary: null,
  servings: 4,
  prepMinutes: null,
  cookMinutes: 20,
  totalMinutes: 20,
  difficulty: RecipeDifficulty.medium,
  imageUrl: null,
  source: RecipeSource.curated,
  tags: [],
  mealSlots: [],
);

typedef _Request = ({
  String? query,
  String? slot,
  String? tag,
  int? minutes,
  int page,
});

Finder _recipeBrowseScrollable() => find.ancestor(
  of: find.descendant(
    of: find.byKey(const ValueKey('recipe-browse-scroll')),
    matching: find.byType(SliverGrid),
  ),
  matching: find.byType(Scrollable),
);

RecipePage _page(int page, List<RecipeItem> items, {int pages = 1}) =>
    RecipePage(
      page: page,
      size: 20,
      totalElements: pages == 1 ? items.length : 2,
      totalPages: pages,
      content: items,
    );

class _FakeRepository implements RecipeRepository {
  final requests = <_Request>[];
  Future<RecipePage> Function(_Request)? onRecipes;
  Future<List<RecipeTag>> Function()? onTags;
  Future<List<RecipeMealSlot>> Function()? onSlots;
  int tagCalls = 0;
  int slotCalls = 0;

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
      slot: mealSlotCode,
      tag: tagCode,
      minutes: maxMinutes,
      page: page,
    );
    requests.add(request);
    return onRecipes?.call(request) ?? Future.value(_page(0, [_first]));
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

  @override
  Future<RecipeDetail> getRecipe(String publicId) => throw UnimplementedError();
}

void main() {
  late _FakeRepository repository;
  late RecipeController controller;

  setUp(() {
    repository = _FakeRepository();
    controller = RecipeController(repository: repository);
  });
  tearDown(() => controller.dispose());

  Future<void> show(
    WidgetTester tester, {
    ValueChanged<RecipeItem>? selected,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        home: RecipeBrowsePage(
          controller: controller,
          onRecipeSelected: selected,
        ),
      ),
    );
    await tester.pump();
  }

  testWidgets('loading, card content, placeholder, and selection', (
    tester,
  ) async {
    final pending = Completer<RecipePage>();
    repository.onRecipes = (_) => pending.future;
    RecipeItem? selected;
    await show(tester, selected: (item) => selected = item);
    expect(
      find.byKey(const ValueKey('recipe-initial-loading')),
      findsOneWidget,
    );
    pending.complete(_page(0, [_first]));
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
    expect(find.text('Fresh and quick'), findsOneWidget);
    expect(find.text(AppStrings.recipeImageUnavailable), findsOneWidget);
    expect(find.text('Dinner'), findsWidgets);
    expect(find.text('Vegan'), findsWidgets);
    await tester.tap(find.byKey(const ValueKey('recipe-item-recipe-a')));
    expect(selected?.publicId, 'recipe-a');
  });

  testWidgets('empty result keeps search and filters', (tester) async {
    repository.onRecipes = (_) async => _page(0, []);
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.recipeNoResults), findsOneWidget);
    expect(find.byKey(const ValueKey('recipe-search-field')), findsOneWidget);
    expect(find.byKey(const ValueKey('recipe-clear-filters')), findsOneWidget);
  });

  testWidgets('initial error retries without exposing raw exception', (
    tester,
  ) async {
    var fail = true;
    repository.onRecipes = (_) async {
      if (fail) throw const ApiResponseFormatException();
      return _page(0, [_first]);
    };
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('recipe-initial-retry')), findsOneWidget);
    expect(find.textContaining('ApiResponseFormatException'), findsNothing);
    fail = false;
    await tester.tap(find.byKey(const ValueKey('recipe-initial-retry')));
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
  });

  testWidgets('search submits trimmed query and empty query clears it', (
    tester,
  ) async {
    await show(tester);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('recipe-search-field')),
      '  rice  ',
    );
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(repository.requests.last.query, 'rice');
    await tester.enterText(
      find.byKey(const ValueKey('recipe-search-field')),
      '  ',
    );
    await tester.tap(find.byKey(const ValueKey('recipe-search-submit')));
    await tester.pumpAndSettle();
    expect(repository.requests.last.query, '');
  });

  testWidgets('search renders the server result set', (tester) async {
    repository.onRecipes = (request) async =>
        _page(0, request.query == 'rice' ? [_first] : [_second]);
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text('Noodle soup'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('recipe-search-field')),
      'rice',
    );
    await tester.tap(find.byKey(const ValueKey('recipe-search-submit')));
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
    expect(find.text('Noodle soup'), findsNothing);
  });

  testWidgets('tag loading leaves catalog visible and populates filter', (
    tester,
  ) async {
    final pending = Completer<List<RecipeTag>>();
    repository.onTags = () => pending.future;
    await show(tester);
    await tester.pump();
    expect(find.text(AppStrings.recipeTagsLoading), findsOneWidget);
    expect(find.text('Rice bowl'), findsOneWidget);
    pending.complete([_tag]);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('recipe-tag-filter')));
    await tester.pumpAndSettle();
    expect(find.text('Vegan'), findsWidgets);
  });

  testWidgets('backend reference codes combine with max minutes and clear', (
    tester,
  ) async {
    await show(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('recipe-meal-slot-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dinner').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('recipe-tag-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Vegan').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('recipe-max-minutes-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('30 ${AppStrings.recipeMinutes}').last);
    await tester.pumpAndSettle();
    expect(repository.requests.last.slot, 'DINNER');
    expect(repository.requests.last.tag, 'VEGAN');
    expect(repository.requests.last.minutes, 30);
    expect(repository.requests.last.page, 0);
    await tester.tap(find.byKey(const ValueKey('recipe-clear-filters')));
    await tester.pumpAndSettle();
    expect(repository.requests.last.slot, isNull);
    expect(repository.requests.last.tag, isNull);
    expect(repository.requests.last.minutes, isNull);
  });

  testWidgets('reference errors keep catalog and retry independently', (
    tester,
  ) async {
    var failTag = true;
    repository.onTags = () async {
      if (failTag) throw const ApiResponseFormatException();
      return [_tag];
    };
    await show(tester);
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
    expect(find.byKey(const ValueKey('recipe-tags-retry')), findsOneWidget);
    expect(repository.slotCalls, 1);
    failTag = false;
    await tester.tap(find.byKey(const ValueKey('recipe-tags-retry')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('recipe-tags-retry')), findsNothing);
    expect(repository.tagCalls, 2);
    expect(repository.slotCalls, 1);
  });

  testWidgets('meal-slot reference loading and failure do not remove cards', (
    tester,
  ) async {
    final pending = Completer<List<RecipeMealSlot>>();
    repository.onSlots = () => pending.future;
    await show(tester);
    await tester.pump();
    expect(find.text(AppStrings.recipeMealSlotsLoading), findsOneWidget);
    pending.completeError(const ApiResponseFormatException());
    await tester.pumpAndSettle();
    expect(find.text('Rice bowl'), findsOneWidget);
    expect(find.byKey(const ValueKey('recipe-slots-retry')), findsOneWidget);
    repository.onSlots = () async => [_slot];
    await tester.tap(find.byKey(const ValueKey('recipe-slots-retry')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('recipe-slots-retry')), findsNothing);
  });

  testWidgets(
    'load more preserves cards, guards repeats, and ends at last page',
    (tester) async {
      final pending = Completer<RecipePage>();
      repository.onRecipes = (request) => request.page == 0
          ? Future.value(_page(0, [_first], pages: 2))
          : pending.future;
      await show(tester);
      await tester.pumpAndSettle();
      expect(controller.catalogState.page, 0);
      expect(controller.catalogState.hasMore, isTrue);
      final browseScrollable = _recipeBrowseScrollable();
      expect(browseScrollable, findsOneWidget);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('recipe-load-more')),
        200,
        scrollable: browseScrollable,
      );
      expect(find.byKey(const ValueKey('recipe-load-more')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('recipe-load-more')));
      await tester.pump();
      expect(find.text('Rice bowl'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('recipe-load-more-loading')),
        findsOneWidget,
      );
      await controller.loadMore();
      expect(
        repository.requests.where((request) => request.page == 1).length,
        1,
      );
      expect(repository.requests.last.page, 1);
      pending.complete(_page(1, [_second], pages: 2));
      await tester.pumpAndSettle();
      expect(controller.catalogState.page, 1);
      expect(controller.catalogState.hasMore, isFalse);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('recipe-item-recipe-b')),
        200,
        scrollable: browseScrollable,
      );
      expect(find.text('Noodle soup'), findsOneWidget);
      expect(find.byKey(const ValueKey('recipe-load-more')), findsNothing);
    },
  );

  testWidgets('load-more error preserves cards and can retry', (tester) async {
    var fail = true;
    repository.onRecipes = (request) async {
      if (request.page == 0) return _page(0, [_first], pages: 2);
      if (fail) throw const ApiResponseFormatException();
      return _page(1, [_second], pages: 2);
    };
    await show(tester);
    await tester.pumpAndSettle();
    expect(controller.catalogState.page, 0);
    expect(controller.catalogState.hasMore, isTrue);
    final browseScrollable = _recipeBrowseScrollable();
    expect(browseScrollable, findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('recipe-load-more')),
      200,
      scrollable: browseScrollable,
    );
    expect(find.byKey(const ValueKey('recipe-load-more')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('recipe-load-more')));
    await tester.pumpAndSettle();
    expect(repository.requests.last.page, 1);
    expect(controller.catalogState.page, 0);
    expect(controller.catalogState.hasMore, isTrue);
    expect(find.text('Rice bowl'), findsOneWidget);
    expect(controller.catalogState.loadMoreErrorMessage, isNotNull);
    final retryFinder = find.byKey(const ValueKey('recipe-load-more-retry'));
    expect(retryFinder, findsOneWidget);
    await tester.ensureVisible(retryFinder);
    await tester.pump();
    final hitTestableRetryFinder = retryFinder.hitTestable();
    expect(hitTestableRetryFinder, findsOneWidget);
    fail = false;
    await tester.tap(hitTestableRetryFinder);
    await tester.pump();
    await tester.pumpAndSettle();
    expect(repository.requests.last.page, 1);
    expect(
      repository.requests.where((request) => request.page == 1),
      hasLength(2),
    );
    expect(controller.catalogState.page, 1);
    expect(controller.catalogState.hasMore, isFalse);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('recipe-item-recipe-b')),
      200,
      scrollable: browseScrollable,
    );
    expect(find.text('Noodle soup'), findsOneWidget);
  });

  testWidgets('narrow and wide layouts render without overflow', (
    tester,
  ) async {
    repository.onRecipes = (_) async => _page(0, [_first, _second]);
    await tester.binding.setSurfaceSize(const Size(360, 800));
    await show(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final grid = tester.widget<SliverGrid>(find.byType(SliverGrid));
    expect(
      (grid.gridDelegate as SliverGridDelegateWithFixedCrossAxisCount)
          .crossAxisCount,
      2,
    );
    await tester.binding.setSurfaceSize(null);
  });
}
