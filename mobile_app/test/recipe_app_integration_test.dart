import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_state.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_repository.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_browse_page.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_detail_page.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_pantry_repository.dart';

const _id = '00000000-0000-4000-8000-000000000501';
const _item = RecipeItem(
  publicId: _id,
  title: 'Rice bowl',
  summary: null,
  servings: 2,
  prepMinutes: null,
  cookMinutes: null,
  totalMinutes: 20,
  difficulty: RecipeDifficulty.easy,
  imageUrl: null,
  source: RecipeSource.curated,
  tags: [],
  mealSlots: [],
);

RecipeDetail _detail(String id) => RecipeDetail(
  publicId: id,
  title: 'Rice bowl detail',
  slug: 'rice-bowl',
  summary: null,
  servings: 2,
  prepMinutes: null,
  cookMinutes: null,
  totalMinutes: 20,
  difficulty: RecipeDifficulty.easy,
  instructionsNote: null,
  imageUrl: null,
  source: RecipeSource.curated,
  sourceReference: null,
  status: RecipeStatus.published,
  publishedAt: '2026-09-01T10:00:00',
  ingredients: const [],
  steps: const [],
  tags: const [],
  mealSlots: const [],
  nutrition: null,
);

class _RecipeRepository implements RecipeRepository {
  final listCalls = <int>[];
  final detailCalls = <String>[];
  Future<RecipePage> Function()? onList;

  @override
  Future<RecipePage> getRecipes({
    String? query,
    String? mealSlotCode,
    String? tagCode,
    int? maxMinutes,
    int page = 0,
    int size = 20,
  }) {
    listCalls.add(page);
    return onList?.call() ??
        Future.value(
          const RecipePage(
            page: 0,
            size: 20,
            totalElements: 1,
            totalPages: 1,
            content: [_item],
          ),
        );
  }

  @override
  Future<RecipeDetail> getRecipe(String publicId) {
    detailCalls.add(publicId);
    return Future.value(_detail(publicId));
  }

  @override
  Future<List<RecipeTag>> getRecipeTags() => Future.value([]);

  @override
  Future<List<RecipeMealSlot>> getMealSlotTypes() => Future.value([]);
}

class _SwitchableAuthRepository extends FakeAuthRepository {
  String publicId = '00000000-0000-4000-8000-000000000601';

  @override
  Future<AuthIdentity> me() async => AuthIdentity(
    publicId: publicId,
    email: 'person@example.test',
    roles: const ['ROLE_USER'],
  );
}

SessionController _session(FakeAuthRepository auth) => SessionController(
  authRepository: auth,
  refreshTokenStore: NoRefreshTokenStore(),
  isWeb: true,
);

Future<void> _pump(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}

void main() {
  testWidgets('authenticated browse, selection, detail, and direct routes', (
    tester,
  ) async {
    final session = _session(FakeAuthRepository());
    await session.login('person@example.test', 'Password123!');
    final repository = _RecipeRepository();
    final controller = RecipeController(repository: repository);
    final router = AppRouter(session, recipeController: controller).router;
    addTearDown(() {
      router.dispose();
      controller.dispose();
    });
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    router.go('/recipes');
    await _pump(tester);
    expect(find.byType(RecipeBrowsePage), findsOneWidget);
    expect(find.byKey(const ValueKey('recipe-item-$_id')), findsOneWidget);
    expect(repository.listCalls, [0]);
    final recipeCard = find.byKey(const ValueKey('recipe-item-$_id'));
    final browseScrollable = find.ancestor(
      of: find.descendant(
        of: find.byKey(const ValueKey('recipe-browse-scroll')),
        matching: find.byType(SliverGrid),
      ),
      matching: find.byType(Scrollable),
    );
    expect(browseScrollable, findsOneWidget);
    await tester.scrollUntilVisible(
      recipeCard,
      200,
      scrollable: browseScrollable,
    );
    await tester.ensureVisible(recipeCard);
    await tester.pump();
    expect(recipeCard.hitTestable(), findsOneWidget);
    await tester.tap(recipeCard.hitTestable());
    await tester.pumpAndSettle();
    expect(router.routerDelegate.state.uri.path, '/recipes/$_id');
    expect(
      tester.widget<RecipeDetailPage>(find.byType(RecipeDetailPage)).publicId,
      _id,
    );
    expect(repository.detailCalls, [_id]);
    router.go('/recipes');
    await _pump(tester);
    expect(find.byType(RecipeBrowsePage), findsOneWidget);
    expect(repository.listCalls, [0]);
    router.go('/recipes/$_id');
    await _pump(tester);
    expect(router.routerDelegate.state.uri.path, '/recipes/$_id');
    expect(find.byType(RecipeDetailPage), findsOneWidget);
    expect(repository.detailCalls.last, _id);
  });

  testWidgets(
    'Recipe routes are protected and accepted as safe login destinations',
    (tester) async {
      final anonymous = _session(
        FakeAuthRepository(refreshError: const ApiHttpException(401)),
      );
      await anonymous.bootstrap();
      final router = AppRouter(anonymous).router;
      addTearDown(router.dispose);
      await tester.pumpWidget(MaterialApp.router(routerConfig: router));
      for (final target in ['/recipes', '/recipes/$_id']) {
        router.go(target);
        await tester.pumpAndSettle();
        final uri = router.routerDelegate.currentConfiguration.uri;
        expect(uri.path, '/auth/login');
        expect(uri.queryParameters['from'], target);
      }

      final authenticated = _session(FakeAuthRepository());
      await authenticated.login('person@example.test', 'Password123!');
      final authenticatedRouter = AppRouter(authenticated).router;
      addTearDown(authenticatedRouter.dispose);
      await tester.pumpWidget(
        MaterialApp.router(routerConfig: authenticatedRouter),
      );
      for (final target in ['/recipes', '/recipes/$_id']) {
        authenticatedRouter.go(
          '/auth/login?from=${Uri.encodeComponent(target)}',
        );
        await tester.pumpAndSettle();
        expect(
          authenticatedRouter.routerDelegate.currentConfiguration.uri.path,
          target,
        );
      }
      authenticatedRouter.go(
        '/auth/login?from=${Uri.encodeComponent('/recipes/bad-id')}',
      );
      await tester.pumpAndSettle();
      expect(
        authenticatedRouter.routerDelegate.currentConfiguration.uri.path,
        '/catalog/foods',
      );
    },
  );

  testWidgets('drawer and rail select Recipes without shifting Pantry', (
    tester,
  ) async {
    final session = _session(FakeAuthRepository());
    await session.login('person@example.test', 'Password123!');
    final controller = RecipeController(repository: _RecipeRepository());
    final pantryController = PantryController(
      repository: FakePantryRepository(),
    );
    final router = AppRouter(
      session,
      recipeController: controller,
      pantryController: pantryController,
    ).router;
    addTearDown(() {
      router.dispose();
      controller.dispose();
      pantryController.dispose();
    });
    await tester.binding.setSurfaceSize(const Size(600, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.tap(find.byIcon(Icons.menu));
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.recipes), findsWidgets);
    await tester.tap(find.byKey(const ValueKey('recipes-nav-drawer')));
    await _pump(tester);
    expect(router.routerDelegate.currentConfiguration.uri.path, '/recipes');
    router.go('/recipes/$_id');
    await _pump(tester);
    await tester.tap(find.byIcon(Icons.menu));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<ListTile>(find.byKey(const ValueKey('recipes-nav-drawer')))
          .selected,
      isTrue,
    );

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.binding.setSurfaceSize(const Size(1100, 800));
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    router.go('/recipes/$_id');
    await _pump(tester);
    expect(
      tester.widget<NavigationRail>(find.byType(NavigationRail)).selectedIndex,
      7,
    );
    await tester.ensureVisible(find.byKey(const ValueKey('recipes-nav-rail')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('recipes-nav-rail')));
    await _pump(tester);
    expect(router.routerDelegate.currentConfiguration.uri.path, '/recipes');
    router.go('/pantry');
    await _pump(tester);
    expect(
      tester.widget<NavigationRail>(find.byType(NavigationRail)).selectedIndex,
      6,
    );
  });

  testWidgets(
    'app session reset clears Recipe state and rejects old response',
    (tester) async {
      final pending = Completer<RecipePage>();
      final repository = _RecipeRepository()..onList = () => pending.future;
      final auth = _SwitchableAuthRepository();
      final session = _session(auth);
      await session.login('first@example.test', 'Password123!');
      final controller = RecipeController(repository: repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        SmartMealPlannerApp(
          sessionController: session,
          recipeController: controller,
        ),
      );
      await _pump(tester);
      await tester.ensureVisible(
        find.byKey(const ValueKey('recipes-nav-rail')),
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('recipes-nav-rail')));
      await _pump(tester);
      expect(repository.listCalls, [0]);
      controller.setTagCode('VEGAN');
      expect(controller.catalogState.filters.tagCode, 'VEGAN');
      await controller.loadDetail(_id);
      expect(controller.detailState.status, RecipeDetailStatus.loaded);
      await session.logout();
      await _pump(tester);
      expect(controller.catalogState.items, isEmpty);
      expect(controller.catalogState.filters.tagCode, isNull);
      expect(controller.detailState.status, RecipeDetailStatus.initial);
      expect(controller.tagsState.status, RecipeReferenceStatus.initial);
      expect(controller.mealSlotsState.status, RecipeReferenceStatus.initial);
      pending.complete(
        const RecipePage(
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          content: [_item],
        ),
      );
      await _pump(tester);
      expect(controller.catalogState.items, isEmpty);
    },
  );

  testWidgets('account switch resets Recipe data and reloads the browse page', (
    tester,
  ) async {
    final oldRequest = Completer<RecipePage>();
    final auth = _SwitchableAuthRepository();
    final session = _session(auth);
    await session.login('first@example.test', 'Password123!');
    final repository = _RecipeRepository();
    repository.onList = () => repository.listCalls.length == 2
        ? oldRequest.future
        : Future.value(
            const RecipePage(
              page: 0,
              size: 20,
              totalElements: 1,
              totalPages: 1,
              content: [_item],
            ),
          );
    final controller = RecipeController(repository: repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      SmartMealPlannerApp(
        sessionController: session,
        recipeController: controller,
      ),
    );
    await _pump(tester);
    await tester.ensureVisible(find.byKey(const ValueKey('recipes-nav-rail')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('recipes-nav-rail')));
    await _pump(tester);
    expect(controller.catalogState.items, hasLength(1));
    final staleRead = controller.search('old account');
    auth.publicId = '00000000-0000-4000-8000-000000000602';
    await session.login('second@example.test', 'Password123!');
    await _pump(tester);
    expect(controller.catalogState.filters.query, isEmpty);
    expect(controller.catalogState.items, hasLength(1));
    expect(repository.listCalls.length, 3);
    oldRequest.complete(
      const RecipePage(
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        content: [],
      ),
    );
    await staleRead;
    await _pump(tester);
    expect(controller.catalogState.items, hasLength(1));
  });

  testWidgets('invalid Recipe detail stays protected and fails safely', (
    tester,
  ) async {
    final session = _session(
      FakeAuthRepository(refreshError: const ApiHttpException(401)),
    );
    await session.bootstrap();
    final router = AppRouter(session).router;
    addTearDown(router.dispose);
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));

    router.go('/recipes/not-a-uuid');
    await tester.pumpAndSettle();
    final login = router.routerDelegate.currentConfiguration.uri;
    expect(login.path, '/auth/login');
    expect(login.queryParameters['from'], '/recipes/not-a-uuid');

    await session.login('person@example.test', 'Password123!');
    await tester.pumpAndSettle();
    expect(router.routerDelegate.state.uri.path, '/catalog/foods');

    router.go('/recipes/not-a-uuid');
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.pageNotFound), findsOneWidget);
    expect(find.textContaining('Exception'), findsNothing);
  });

  testWidgets('Recipe detail push returns to browse and short rail scrolls', (
    tester,
  ) async {
    final session = _session(FakeAuthRepository());
    await session.login('person@example.test', 'Password123!');
    final repository = _RecipeRepository();
    final controller = RecipeController(repository: repository);
    final router = AppRouter(session, recipeController: controller).router;
    addTearDown(() {
      router.dispose();
      controller.dispose();
    });
    await tester.binding.setSurfaceSize(const Size(850, 400));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.ensureVisible(find.byKey(const ValueKey('recipes-nav-rail')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('recipes-nav-rail')));
    await tester.pumpAndSettle();
    expect(router.routerDelegate.state.uri.path, '/recipes');
    expect(tester.takeException(), isNull);

    router.push('/recipes/$_id');
    await tester.pumpAndSettle();
    expect(router.routerDelegate.state.uri.path, '/recipes/$_id');
    expect(find.byType(RecipeDetailPage), findsOneWidget);
    router.pop();
    await tester.pumpAndSettle();
    expect(router.routerDelegate.state.uri.path, '/recipes');
    expect(find.byType(RecipeBrowsePage), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
