import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';
import 'package:smart_meal_planner/features/preferences/presentation/preferences_page.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_catalog_repository.dart';
import 'support/fake_disliked_ingredients.dart';

void main() {
  testWidgets('authenticated preferences route includes the private section', (
    tester,
  ) async {
    final session = _session();
    await session.login('user@example.test', 'Password123!');
    final dislikedController = _dislikedController();
    final router = AppRouter(
      session,
      preferencesController: _preferencesController(),
      dislikedIngredientsController: dislikedController,
    ).router;
    addTearDown(() {
      dislikedController.dispose();
      router.dispose();
    });

    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    router.go('/preferences');
    await _pumpAsync(tester);

    expect(
      find.byKey(const ValueKey('disliked-ingredients-section')),
      findsOneWidget,
    );
  });

  testWidgets('preferences keeps dietary/allergen sections and renders saved avoidance', (
    tester,
  ) async {
    final dislikedController = _dislikedController(
      preferences: [_savedPreference],
    );
    addTearDown(dislikedController.dispose);

    await tester.pumpWidget(
      _page(
        preferencesController: _preferencesController(),
        dislikedController: dislikedController,
      ),
    );
    await _pumpAsync(tester);

    expect(find.byKey(const ValueKey('dietary-preferences-section')), findsOneWidget);
    expect(find.byKey(const ValueKey('allergens-section')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('disliked-ingredients-section')),
      findsOneWidget,
    );
    expect(find.text(_savedPreference.ingredientDisplayName), findsOneWidget);
    expect(find.text(AppStrings.dislikedIngredientAvoid), findsOneWidget);
  });

  testWidgets('picker adds an ingredient once with DISLIKE default', (tester) async {
    final dislikedController = _dislikedController();
    addTearDown(dislikedController.dispose);
    await tester.pumpWidget(
      _page(
        preferencesController: _preferencesController(),
        dislikedController: dislikedController,
      ),
    );
    await _pumpAsync(tester);

    expect(find.text(AppStrings.dislikedIngredientsEmpty), findsOneWidget);
    final addButton = find.byKey(const ValueKey('disliked-add-ingredient'));
    await tester.ensureVisible(addButton);
    await tester.pump();
    await tester.tap(addButton);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.byKey(const ValueKey('disliked-picker')), findsOneWidget);
    final item = find.byKey(
      const ValueKey('disliked-picker-item-$testIngredientId'),
    );
    expect(item, findsOneWidget);
    expect(find.text(testIngredient.displayName), findsOneWidget);
    await tester.tap(item);
    await tester.pump();

    expect(dislikedController.state.draftPreferences, hasLength(1));
    expect(
      dislikedController.state.draftPreferences.single.strength,
      DislikedIngredientStrength.dislike,
    );
    expect(find.text(AppStrings.dislikedIngredientPickerSelected), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('disliked-picker-close')),
    );
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      find.byKey(const ValueKey('disliked-editor-$testIngredientId')),
      findsOneWidget,
    );
  });

  testWidgets('strength, note validation, and remove update only the draft', (
    tester,
  ) async {
    final repository = FakeDislikedIngredientsRepository(
      preferences: [_savedDislikePreference],
    );
    final dislikedController = _dislikedController(repository: repository);
    addTearDown(dislikedController.dispose);
    await tester.pumpWidget(
      _page(
        preferencesController: _preferencesController(),
        dislikedController: dislikedController,
      ),
    );
    await _pumpAsync(tester);

    expect(find.text(AppStrings.dislikedIngredientDislike), findsOneWidget);
    final strength = find.byKey(
      const ValueKey('disliked-strength-$testIngredientId'),
    );
    await tester.ensureVisible(strength);
    await tester.pump();
    await tester.tap(strength);
    await tester.pump();
    await tester.tap(find.text(AppStrings.dislikedIngredientAvoid).last);
    await tester.pump();
    expect(
      dislikedController.state.draftPreferences.single.strength,
      DislikedIngredientStrength.avoid,
    );

    final note = find.byKey(
      const ValueKey('disliked-note-$testIngredientId'),
    );
    await tester.ensureVisible(note);
    await tester.pump();
    await tester.enterText(note, 'x' * 256);
    await tester.pump();

    final save = find.byKey(const ValueKey('save-disliked-ingredients'));
    await tester.ensureVisible(save);
    await tester.pump();
    await tester.tap(save);
    await tester.pump();

    expect(repository.saveCalls, isEmpty);
    expect(find.text(AppStrings.dislikedIngredientNoteTooLong), findsOneWidget);
    expect(dislikedController.state.draftPreferences, hasLength(1));

    final remove = find.byKey(
      const ValueKey('disliked-remove-$testIngredientId'),
    );
    await tester.ensureVisible(remove);
    await tester.pump();
    await tester.tap(remove);
    await tester.pump();
    expect(dislikedController.state.draftPreferences, isEmpty);
    expect(dislikedController.state.savedPreferences, hasLength(1));
  });

  testWidgets('picker pagination and narrow layout remain interactive', (tester) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.binding.setSurfaceSize(const Size(360, 800));
    final catalogRepository = FakeCatalogRepository(
      ingredientPage: IngredientCatalogPage(
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 2,
        content: [testIngredient],
      ),
    )..onGetIngredients = (request) => Future.value(
      request.page == 0
          ? IngredientCatalogPage(
              page: 0,
              size: 20,
              totalElements: 2,
              totalPages: 2,
              content: [testIngredient],
            )
          : IngredientCatalogPage(
              page: 1,
              size: 20,
              totalElements: 2,
              totalPages: 2,
              content: [_secondIngredient],
            ),
    );
    final dislikedController = _dislikedController(
      catalogRepository: catalogRepository,
    );
    addTearDown(dislikedController.dispose);
    await tester.pumpWidget(
      _page(
        preferencesController: _preferencesController(),
        dislikedController: dislikedController,
      ),
    );
    await _pumpAsync(tester);

    final addButton = find.byKey(const ValueKey('disliked-add-ingredient'));
    await tester.ensureVisible(addButton);
    await tester.pump();
    await tester.tap(addButton);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    final loadMore = find.byKey(const ValueKey('disliked-picker-load-more'));
    await tester.ensureVisible(loadMore);
    await tester.pump();
    await tester.tap(loadMore);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 1));

    expect(
      find.byKey(
        const ValueKey('disliked-picker-item-$testIngredientIdTwo'),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('save error keeps visible local edits', (tester) async {
    final dislikedController = _dislikedController(
      repository: FakeDislikedIngredientsRepository(
        preferences: [_savedPreference],
        saveError: const ApiTransportException(ApiTransportFailureKind.network),
      ),
    );
    addTearDown(dislikedController.dispose);
    await tester.pumpWidget(
      _page(
        preferencesController: _preferencesController(),
        dislikedController: dislikedController,
      ),
    );
    await _pumpAsync(tester);

    final remove = find.byKey(
      const ValueKey('disliked-remove-$testIngredientId'),
    );
    await tester.ensureVisible(remove);
    await tester.pump();
    await tester.tap(remove);
    await tester.pump();
    final add = find.byKey(const ValueKey('disliked-add-ingredient'));
    await tester.ensureVisible(add);
    await tester.pump();
    await tester.tap(add);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(
      find.byKey(const ValueKey('disliked-picker-item-$testIngredientId')),
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('disliked-picker-close')));
    await tester.pump(const Duration(milliseconds: 300));

    final save = find.byKey(const ValueKey('save-disliked-ingredients'));
    await tester.ensureVisible(save);
    await tester.pump();
    await tester.tap(save);
    await tester.pump();

    expect(
      find.byKey(const ValueKey('disliked-editor-$testIngredientId')),
      findsOneWidget,
    );
    expect(dislikedController.state.hasChanges, isTrue);
  });
}

Widget _page({
  required PreferencesController preferencesController,
  required DislikedIngredientsController dislikedController,
}) => MaterialApp(
  home: PreferencesPage(
    sessionController: _session(),
    preferencesController: preferencesController,
    dislikedIngredientsController: dislikedController,
  ),
);

PreferencesController _preferencesController() => PreferencesController(
  repository: _PreferencesRepository(),
);

DislikedIngredientsController _dislikedController({
  List<DislikedIngredientPreference>? preferences,
  FakeDislikedIngredientsRepository? repository,
  FakeCatalogRepository? catalogRepository,
}) => DislikedIngredientsController(
  repository:
      repository ??
      FakeDislikedIngredientsRepository(preferences: preferences ?? const []),
  catalogRepository: catalogRepository ?? FakeCatalogRepository(),
);

SessionController _session() => SessionController(
  authRepository: FakeAuthRepository(),
  refreshTokenStore: NoRefreshTokenStore(),
  isWeb: true,
);

Future<void> _pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}

final class _PreferencesRepository implements PreferencesRepository {
  @override
  Future<List<DietaryPreferenceReference>>
  getDietaryPreferenceReferences() async => const [
    DietaryPreferenceReference(
      code: 'VEGAN',
      displayName: 'Vegan',
      description: 'Plant-based meals.',
      isExclusionary: true,
      displayOrder: 1,
    ),
  ];

  @override
  Future<List<SelectedDietaryPreference>>
  getSelectedDietaryPreferences() async => const [];

  @override
  Future<List<SelectedDietaryPreference>> replaceDietaryPreferences(
    List<String> codes,
  ) async => const [];

  @override
  Future<List<AllergenReference>> getAllergenReferences() async => const [
    AllergenReference(
      code: 'PEANUT',
      displayName: 'Peanuts',
      description: 'Peanuts and peanut products.',
      displayOrder: 1,
    ),
  ];

  @override
  Future<List<SelectedAllergen>> getSelectedAllergens() async => const [];

  @override
  Future<List<SelectedAllergen>> replaceAllergens(
    List<AllergenSelection> selections,
  ) async => const [];
}

final _savedPreference = DislikedIngredientPreference(
  ingredientPublicId: testIngredientId,
  ingredientCode: testIngredient.code,
  ingredientDisplayName: testIngredient.displayName,
  category: testCategory,
  strength: DislikedIngredientStrength.avoid,
  note: null,
);

final _savedDislikePreference = DislikedIngredientPreference(
  ingredientPublicId: testIngredientId,
  ingredientCode: testIngredient.code,
  ingredientDisplayName: testIngredient.displayName,
  category: testCategory,
  strength: DislikedIngredientStrength.dislike,
  note: null,
);

const testIngredientIdTwo = '00000000-0000-4000-8000-000000000102';

const _secondIngredient = IngredientCatalogItem(
  publicId: testIngredientIdTwo,
  code: 'ING_SMILING_VN_1002',
  displayName: 'Cà chua',
  category: testCategory,
  staple: false,
);
