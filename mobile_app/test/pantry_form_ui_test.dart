import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_ingredient_picker.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_catalog_repository.dart';
import 'support/fake_pantry_repository.dart';

const _secondIngredientId = '00000000-0000-4000-8000-000000000103';

void main() {
  testWidgets('create validates required fields and decimal text locally', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/new');
    await _pump(tester);
    await _save(tester);
    expect(fixture.pantry.createRequests, isEmpty);
    expect(find.text(AppStrings.pantryIngredientRequired), findsOneWidget);
    expect(find.text(AppStrings.pantryQuantityInvalid), findsOneWidget);
    expect(find.text(AppStrings.pantryUnitRequired), findsOneWidget);
    expect(find.text(AppStrings.pantryStorageRequired), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-quantity')),
      '1e2',
    );
    await _save(tester);
    expect(fixture.pantry.createRequests, isEmpty);
    expect(find.text(AppStrings.pantryQuantityInvalid), findsOneWidget);
  });

  for (final quantity in ['0.0001', '1.2345', '99999999.9999']) {
    testWidgets('create sends exact decimal $quantity and arbitrary unit', (
      tester,
    ) async {
      final fixture = await _fixture();
      await tester.binding.setSurfaceSize(const Size(1100, 1300));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      fixture.pantry.onCreate = (_) async {
        fixture.pantry.detail = pantryItem(publicId: testPantryIdTwo);
        return fixture.pantry.detail!;
      };
      await tester.pumpWidget(fixture.app);
      fixture.router.go('/pantry/new');
      await _pump(tester);
      await _chooseIngredient(tester, testIngredientId);
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const ValueKey('pantry-form-unit')),
            )
            .controller!
            .text,
        'g',
      );
      await tester.enterText(
        find.byKey(const ValueKey('pantry-form-quantity')),
        quantity,
      );
      await tester.enterText(
        find.byKey(const ValueKey('pantry-form-unit')),
        'sack',
      );
      await _chooseStorage(tester, AppStrings.pantryStorageFridge);
      await _save(tester);
      expect(fixture.pantry.createRequests, hasLength(1));
      final json = fixture.pantry.createRequests.single.toJson();
      expect(json['quantity'].toString(), quantity);
      expect(json['unitCode'], 'sack');
      expect(
        fixture.router.routerDelegate.currentConfiguration.uri.path,
        '/pantry/$testPantryIdTwo',
      );
      expect(find.text(AppStrings.pantryCreateSucceeded), findsOneWidget);
    });
  }

  testWidgets('optional food is mapped and a new ingredient clears it', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    fixture.catalog.onGetIngredients = (request) async => IngredientCatalogPage(
      page: request.page,
      size: request.size,
      totalElements: 2,
      totalPages: 1,
      content: [testIngredient, _secondIngredient],
    );
    fixture.catalog.onGetIngredient = (id) async =>
        id == testIngredientId ? testIngredientDetail : _secondDetail;
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/new');
    await _pump(tester);
    await _chooseIngredient(tester, testIngredientId);
    await tester.tap(find.byType(DropdownButtonFormField<String?>));
    await tester.pumpAndSettle();
    await tester.tap(
      find.text(testIngredientDetail.foodMappings.single.foodDisplayName).last,
    );
    await _pump(tester);
    await tester.tap(find.byKey(const ValueKey('pantry-select-ingredient')));
    await _pump(tester);
    await tester.tap(
      find.byKey(const ValueKey('pantry-pick-$_secondIngredientId')),
    );
    await _pump(tester);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('pantry-form-unit')))
          .controller!
          .text,
      'crate',
    );
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-quantity')),
      '1',
    );
    await _chooseStorage(tester, AppStrings.pantryStoragePantry);
    await _save(tester);
    expect(fixture.pantry.createRequests.single.foodPublicId, isNull);
    expect(
      fixture.pantry.createRequests.single.ingredientPublicId,
      _secondIngredientId,
    );
    expect(fixture.pantry.createRequests.single.unitCode, 'crate');
  });

  testWidgets('failed create keeps quantity, unit and note for retry', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    fixture.pantry.createError = const ApiHttpException(409);
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/new');
    await _pump(tester);
    await _chooseIngredient(tester, testIngredientId);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-quantity')),
      '1.2345',
    );
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-unit')),
      'bag',
    );
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-note')),
      '  keep draft  ',
    );
    await _chooseStorage(tester, AppStrings.pantryStorageFridge);
    await _save(tester);
    expect(find.byKey(const ValueKey('pantry-form-error')), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(
            find.byKey(const ValueKey('pantry-form-quantity')),
          )
          .controller!
          .text,
      '1.2345',
    );
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('pantry-form-note')))
          .controller!
          .text,
      '  keep draft  ',
    );
    fixture.pantry.createError = null;
    await _save(tester);
    expect(fixture.pantry.createRequests, hasLength(2));
    expect(fixture.pantry.createRequests.last.toJson()['note'], 'keep draft');
  });

  testWidgets('mapped food UUID is sent only after choosing a mapping', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/new');
    await _pump(tester);
    await _chooseIngredient(tester, testIngredientId);
    await tester.tap(find.byType(DropdownButtonFormField<String?>));
    await tester.pumpAndSettle();
    await tester.tap(
      find.text(testIngredientDetail.foodMappings.single.foodDisplayName).last,
    );
    await _pump(tester);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-quantity')),
      '1',
    );
    await _chooseStorage(tester, AppStrings.pantryStoragePantry);
    await _save(tester);
    expect(
      fixture.pantry.createRequests.single.foodPublicId,
      testIngredientDetail.foodMappings.single.foodPublicId,
    );
  });

  testWidgets('quantity limits and note length stop create before HTTP', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/new');
    await _pump(tester);
    await _chooseIngredient(tester, testIngredientId);
    await _chooseStorage(tester, AppStrings.pantryStoragePantry);
    for (final quantity in [
      '0',
      '-1',
      '100000000',
      '99999999.99999',
      '1.23456',
    ]) {
      await tester.enterText(
        find.byKey(const ValueKey('pantry-form-quantity')),
        quantity,
      );
      await _save(tester);
      expect(fixture.pantry.createRequests, isEmpty, reason: quantity);
    }
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-quantity')),
      '1',
    );
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-note')),
      'a' * 256,
    );
    await _save(tester);
    expect(fixture.pantry.createRequests, isEmpty);
    expect(find.text(AppStrings.pantryNoteTooLong), findsOneWidget);
  });

  testWidgets('edit initializes metadata and clears nullable fields in PUT', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    fixture.pantry.detail = pantryItem(
      expiryDate: DateTime(2026, 2, 3),
      note: 'Original',
    );
    fixture.pantry.onUpdateMetadata = (_, _) async {
      fixture.pantry.detail = pantryItem(
        storageLocation: PantryStorageLocation.freezer,
        acquiredOnPresent: false,
      );
      return fixture.pantry.detail!;
    };
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId/edit');
    await _pump(tester);
    expect(find.byKey(const ValueKey('pantry-form-quantity')), findsNothing);
    expect(find.byKey(const ValueKey('pantry-form-unit')), findsNothing);
    expect(find.byKey(const ValueKey('pantry-form-food')), findsNothing);
    expect(find.textContaining('2026-02-03'), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('pantry-form-note')))
          .controller!
          .text,
      'Original',
    );
    await tester.tap(find.byKey(const ValueKey('pantry-clear-acquired')));
    await tester.tap(find.byKey(const ValueKey('pantry-clear-expiry')));
    await tester.enterText(find.byKey(const ValueKey('pantry-form-note')), ' ');
    await _chooseStorage(tester, AppStrings.pantryStorageFreezer);
    await _save(tester);
    final json = fixture.pantry.updateRequests.single.$2.toJson();
    expect(json, {
      'storageLocation': 'FREEZER',
      'acquiredOn': null,
      'expiryDate': null,
      'expiryKind': 'UNKNOWN',
      'expiryConfidence': 'UNKNOWN',
      'note': null,
    });
    expect(json.containsKey('quantity'), isFalse);
    expect(
      fixture.controller.detailState.item?.storageLocation,
      PantryStorageLocation.freezer,
    );
    expect(find.text(AppStrings.pantryMetadataUpdated), findsOneWidget);
  });

  testWidgets('create form renders at compact and wide sizes', (tester) async {
    final fixture = await _fixture();
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.binding.setSurfaceSize(const Size(320, 640));
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/new');
    await _pump(tester);
    expect(find.byKey(const ValueKey('pantry-create-form')), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.binding.setSurfaceSize(const Size(390, 844));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.binding.setSurfaceSize(const Size(1280, 900));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('pantry-create-form')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed metadata edit preserves draft', (tester) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    fixture.pantry.detail = pantryItem(note: 'Old');
    fixture.pantry.updateError = const ApiHttpException(409);
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId/edit');
    await _pump(tester);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-form-note')),
      'New draft',
    );
    await _save(tester);
    expect(find.byKey(const ValueKey('pantry-form-error')), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('pantry-form-note')))
          .controller!
          .text,
      'New draft',
    );
    expect(
      fixture.router.routerDelegate.currentConfiguration.uri.path,
      '/pantry/$testPantryId/edit',
    );
  });

  testWidgets('edit rejects expiry before acquisition and preserves draft', (
    tester,
  ) async {
    final fixture = await _fixture();
    await tester.binding.setSurfaceSize(const Size(1100, 1300));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    fixture.pantry.detail = pantryItem(
      acquiredOnOverride: DateTime(2026, 3, 3),
      expiryDate: DateTime(2026, 3, 2),
      note: 'Keep',
    );
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId/edit');
    await _pump(tester);
    await _save(tester);
    expect(fixture.pantry.updateRequests, isEmpty);
    expect(find.text(AppStrings.pantryExpiryInvalid), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('pantry-form-note')))
          .controller!
          .text,
      'Keep',
    );
  });

  testWidgets('picker empty, error retry and next page', (tester) async {
    final catalog = FakeCatalogRepository();
    catalog.onGetIngredients = (request) async {
      if (request.query == 'missing') {
        return const IngredientCatalogPage(
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          content: [],
        );
      }
      if (request.query == 'error' && catalog.ingredientRequests.length == 3) {
        throw const ApiTransportException(ApiTransportFailureKind.network);
      }
      if (request.page == 1) {
        return const IngredientCatalogPage(
          page: 1,
          size: 20,
          totalElements: 2,
          totalPages: 2,
          content: [_secondIngredient],
        );
      }
      return const IngredientCatalogPage(
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 2,
        content: [testIngredient],
      );
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PantryIngredientPicker(repository: catalog, onSelected: (_) {}),
        ),
      ),
    );
    await _pump(tester);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-ingredient-search')),
      'missing',
    );
    await tester.tap(
      find.byKey(const ValueKey('pantry-ingredient-search-submit')),
    );
    await _pump(tester);
    expect(find.text(AppStrings.pantryIngredientNoResults), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-ingredient-search')),
      'error',
    );
    await tester.tap(
      find.byKey(const ValueKey('pantry-ingredient-search-submit')),
    );
    await _pump(tester);
    expect(find.byKey(const ValueKey('pantry-picker-retry')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pantry-picker-retry')));
    await _pump(tester);
    expect(
      find.byKey(const ValueKey('pantry-pick-$testIngredientId')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('pantry-picker-more')));
    await _pump(tester);
    expect(
      find.byKey(const ValueKey('pantry-pick-$_secondIngredientId')),
      findsOneWidget,
    );
  });

  testWidgets('picker ignores older search and paginates current query', (
    tester,
  ) async {
    final catalog = FakeCatalogRepository();
    final first = Completer<IngredientCatalogPage>();
    final second = Completer<IngredientCatalogPage>();
    catalog.onGetIngredients = (request) {
      if (request.query == 'old') return first.future;
      if (request.query == 'new') return second.future;
      return Future.value(
        const IngredientCatalogPage(
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          content: [],
        ),
      );
    };
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PantryIngredientPicker(repository: catalog, onSelected: (_) {}),
        ),
      ),
    );
    await _pump(tester);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-ingredient-search')),
      'old',
    );
    await tester.tap(
      find.byKey(const ValueKey('pantry-ingredient-search-submit')),
    );
    await _pump(tester);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-ingredient-search')),
      'new',
    );
    await tester.tap(
      find.byKey(const ValueKey('pantry-ingredient-search-submit')),
    );
    await _pump(tester);
    second.complete(
      const IngredientCatalogPage(
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        content: [_secondIngredient],
      ),
    );
    await _pump(tester);
    first.complete(
      const IngredientCatalogPage(
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        content: [testIngredient],
      ),
    );
    await _pump(tester);
    expect(
      find.byKey(const ValueKey('pantry-pick-$_secondIngredientId')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('pantry-pick-$testIngredientId')),
      findsNothing,
    );
  });
}

const _secondIngredient = IngredientCatalogItem(
  publicId: _secondIngredientId,
  code: 'CRATE',
  displayName: 'Thùng rau',
  category: testCategory,
  staple: false,
);

const _secondDetail = IngredientCatalogDetail(
  publicId: _secondIngredientId,
  code: 'CRATE',
  displayName: 'Thùng rau',
  category: testCategory,
  defaultFoodPublicId: null,
  defaultFoodCode: null,
  defaultFoodDisplayName: null,
  defaultUnitCode: 'crate',
  defaultUnitDisplayName: 'thùng',
  pieceGramWeight: null,
  typicalShelfLifeDays: null,
  staple: false,
  aliases: [],
  foodMappings: [],
  allergens: [],
  unitConversions: [],
);

Future<
  ({
    GoRouter router,
    Widget app,
    FakePantryRepository pantry,
    FakeCatalogRepository catalog,
    PantryController controller,
  })
>
_fixture() async {
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  await session.login('user@example.test', 'Password123!');
  final pantry = FakePantryRepository();
  final catalog = FakeCatalogRepository();
  final controller = PantryController(repository: pantry);
  final router = AppRouter(
    session,
    pantryController: controller,
    pantryCatalogRepository: catalog,
  ).router;
  addTearDown(() {
    router.dispose();
    controller.dispose();
  });
  return (
    router: router,
    app: MaterialApp.router(
      routerConfig: router,
      locale: const Locale('vi', 'VN'),
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('vi', 'VN')],
    ),
    pantry: pantry,
    catalog: catalog,
    controller: controller,
  );
}

Future<void> _chooseIngredient(WidgetTester tester, String id) async {
  await tester.tap(find.byKey(const ValueKey('pantry-select-ingredient')));
  await _pump(tester);
  await tester.tap(find.byKey(ValueKey('pantry-pick-$id')));
  await _pump(tester);
}

Future<void> _chooseStorage(WidgetTester tester, String label) async {
  await tester.tap(find.byKey(const ValueKey('pantry-form-storage')));
  await tester.pumpAndSettle();
  await tester.tap(find.text(label).last);
  await _pump(tester);
}

Future<void> _save(WidgetTester tester) async {
  final key = find.byKey(const ValueKey('pantry-form-save'));
  await tester.ensureVisible(key);
  await tester.tap(key);
  await _pump(tester);
}

Future<void> _pump(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}
