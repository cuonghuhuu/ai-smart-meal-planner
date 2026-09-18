import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/catalog/application/food_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_catalog_repository.dart';

void main() {
  testWidgets('authenticated foods page renders Vietnamese data and search', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final router = _router(
      session,
      foodController: foodController,
    );
    addTearDown(() {
      foodController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    router.go('/catalog/foods');
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('food-list')), findsOneWidget);
    expect(find.text('Gạo nếp cái'), findsOneWidget);
    expect(find.byKey(const ValueKey('food-item-$testFoodId')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('food-item-$testFoodId')));
    await _pumpAsync(tester);
    expect(find.byKey(const ValueKey('food-detail-$testFoodId')), findsOneWidget);
    router.go('/catalog/foods');
    await _pumpAsync(tester);

    await tester.enterText(
      find.byKey(const ValueKey('food-search-field')),
      '  Cà chua  ',
    );
    await tester.tap(find.byKey(const ValueKey('food-search-submit')));
    await _pumpAsync(tester);

    expect(repository.foodRequests.last.query, 'Cà chua');
    expect(repository.foodRequests.last.page, 0);
  });

  testWidgets('food category filter uses the backend category code', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final router = _router(
      session,
      foodController: foodController,
    );
    addTearDown(() {
      foodController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);
    await tester.tap(find.byType(DropdownButton<String>));
    await tester.pump();
    await tester.tap(find.text(AppStrings.catalogCategoryLabels['GRAINS']!).last);
    await _pumpAsync(tester);

    expect(repository.foodRequests.last.categoryCode, 'GRAINS');
    expect(repository.foodRequests.last.page, 0);
  });

  testWidgets('food load more appends rows', (tester) async {
    final repository = FakeCatalogRepository(
      foodPage: _foodPage(page: 0, totalPages: 2, content: [testFood]),
    );
    repository.onGetFoods = (request) => Future<FoodCatalogPage>.value(
      request.page == 1
          ? _foodPage(page: 1, totalPages: 2, content: [testFoodTwo])
          : repository.foodPage,
    );
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final router = _router(
      session,
      foodController: foodController,
    );
    addTearDown(() {
      foodController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);
    await tester.tap(find.byKey(const ValueKey('food-load-more')));
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('food-item-$testFoodIdTwo')), findsOneWidget);
    expect(repository.foodRequests.where((request) => request.page == 1),
        hasLength(1));
  });

  testWidgets('food detail shows returned nutrients without invented zeros', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final router = _router(
      session,
      foodController: foodController,
    );
    addTearDown(() {
      foodController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    router.go('/catalog/foods/$testFoodId');
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('food-detail-$testFoodId')), findsOneWidget);
    expect(find.text('Gạo nếp cái'), findsOneWidget);
    expect(find.textContaining('10.7083'), findsOneWidget);
    expect(find.text('SODIUM'), findsNothing);
    expect(find.text('FIBER'), findsNothing);
  });

  testWidgets('ingredient detail links to the mapped food nutrition page', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final ingredientController = IngredientCatalogController(
      repository: repository,
    );
    final router = _router(
      session,
      foodController: foodController,
      ingredientController: ingredientController,
    );
    addTearDown(() {
      foodController.dispose();
      ingredientController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    router.go('/catalog/ingredients/$testIngredientId');
    await _pumpAsync(tester);

    expect(
      find.byKey(const ValueKey('ingredient-food-$testFoodId')),
      findsOneWidget,
    );
    expect(
      find.text(AppStrings.catalogEnumLabels['UNSPECIFIED']!),
      findsOneWidget,
    );

    final foodLink = find.byKey(
      const ValueKey('ingredient-food-$testFoodId'),
    );
    await tester.ensureVisible(foodLink);
    await tester.pump();
    await tester.tap(foodLink);
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('food-detail-$testFoodId')), findsOneWidget);
  });

  testWidgets('ingredient navigation works from the desktop shell', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final ingredientController = IngredientCatalogController(
      repository: repository,
    );
    final router = _router(
      session,
      foodController: foodController,
      ingredientController: ingredientController,
    );
    addTearDown(() {
      foodController.dispose();
      ingredientController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);
    await tester.tap(
      find.byKey(const ValueKey('catalog-nav-ingredients-rail')),
    );
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('ingredient-list')), findsOneWidget);
    await tester.tap(
      find.byKey(const ValueKey('ingredient-item-$testIngredientId')),
    );
    await _pumpAsync(tester);
    expect(
      find.byKey(const ValueKey('ingredient-detail-$testIngredientId')),
      findsOneWidget,
    );

    router.go('/catalog/ingredients');
    await _pumpAsync(tester);
    await tester.tap(find.text(AppStrings.foods).first);
    await _pumpAsync(tester);
    expect(find.byKey(const ValueKey('food-list')), findsOneWidget);
    expect(find.text('Gạo nếp cái'), findsOneWidget);
  });

  testWidgets('ingredient navigation works at mobile width without overflow', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final ingredientController = IngredientCatalogController(
      repository: repository,
    );
    final router = _router(
      session,
      foodController: foodController,
      ingredientController: ingredientController,
    );
    addTearDown(() {
      foodController.dispose();
      ingredientController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);
    final scaffold = tester.state<ScaffoldState>(find.byType(Scaffold));
    scaffold.openDrawer();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    final ingredientDrawerItem = find.byKey(
      const ValueKey('catalog-nav-ingredients-drawer'),
    );
    expect(ingredientDrawerItem, findsOneWidget);
    await tester.tap(ingredientDrawerItem);
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('ingredient-list')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('catalog routes are protected and restore a safe food detail route', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _anonymousSession();
    final foodController = FoodCatalogController(repository: repository);
    final ingredientController = IngredientCatalogController(
      repository: repository,
    );
    final router = _router(
      session,
      foodController: foodController,
      ingredientController: ingredientController,
    );
    addTearDown(() {
      foodController.dispose();
      ingredientController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);
    expect(find.text(AppStrings.welcomeBack), findsOneWidget);

    router.go('/catalog/ingredients');
    await _pumpAsync(tester);
    expect(find.text(AppStrings.welcomeBack), findsOneWidget);

    router.go('/catalog/ingredients/$testIngredientId');
    await _pumpAsync(tester);
    expect(find.text(AppStrings.welcomeBack), findsOneWidget);

    router.go('/catalog/foods/$testFoodId');
    await _pumpAsync(tester);
    expect(find.text(AppStrings.welcomeBack), findsOneWidget);

    await tester.enterText(find.byType(TextFormField).at(0), 'user@example.test');
    await tester.enterText(find.byType(TextFormField).at(1), 'Password123!');
    await tester.tap(find.widgetWithText(FilledButton, AppStrings.signIn));
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('food-detail-$testFoodId')), findsOneWidget);
  });

  testWidgets('initial catalog failure exposes retry and preserves generic errors', (
    tester,
  ) async {
    final repository = FakeCatalogRepository(
      foodsError: const ApiTransportException(ApiTransportFailureKind.network),
    );
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final router = _router(
      session,
      foodController: foodController,
    );
    addTearDown(() {
      foodController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    await _pumpAsync(tester);
    expect(find.byKey(const ValueKey('food-retry')), findsOneWidget);
    expect(find.textContaining('SocketException'), findsNothing);

    repository.foodsError = null;
    await tester.tap(find.byKey(const ValueKey('food-retry')));
    await _pumpAsync(tester);
    expect(find.text('Gạo nếp cái'), findsOneWidget);
  });

  testWidgets('malicious catalog intended destinations are rejected', (
    tester,
  ) async {
    final repository = FakeCatalogRepository();
    final session = await _authenticatedSession();
    final foodController = FoodCatalogController(repository: repository);
    final router = _router(
      session,
      foodController: foodController,
    );
    addTearDown(() {
      foodController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(_routerApp(router));
    for (final target in [
      'https://evil.example/catalog/foods/$testFoodId',
      '//evil.example/catalog/foods/$testFoodId',
      'javascript:alert(1)',
      '/catalog/foods/not-a-public-id',
      '/catalog/foods/$testFoodId/extra',
    ]) {
      router.go('/auth/login?from=${Uri.encodeComponent(target)}');
      await _pumpAsync(tester);
      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        '/catalog/foods',
        reason: 'rejected intended route: $target',
      );
    }
  });
}

GoRouter _router(
  SessionController session, {
  FoodCatalogController? foodController,
  IngredientCatalogController? ingredientController,
}) => AppRouter(
  session,
  foodCatalogController: foodController,
  ingredientCatalogController: ingredientController,
).router;

Widget _routerApp(GoRouter router) => MaterialApp.router(routerConfig: router);

Future<void> _pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}

Future<SessionController> _anonymousSession() async {
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  await session.bootstrap();
  return session;
}

Future<SessionController> _authenticatedSession() async {
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  await session.login('user@example.test', 'Password123!');
  return session;
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
