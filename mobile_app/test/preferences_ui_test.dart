import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/preferences/data/preferences_repository.dart';
import 'package:smart_meal_planner/features/preferences/presentation/preferences_page.dart';

import 'support/fake_auth_repository.dart';

void main() {
  testWidgets('authenticated /preferences opens reference-driven page', (
    tester,
  ) async {
    final session = await _authenticatedSession();
    final router = AppRouter(
      session,
      preferencesController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/preferences');
    await tester.pumpAndSettle();

    expect(_dietarySectionTitle(), findsOneWidget);
    expect(find.text('Thuần chay'), findsOneWidget);
    expect(find.text('Dị ứng & cần tránh'), findsOneWidget);
    expect(find.byKey(const ValueKey('allergen-search')), findsOneWidget);
    expect(find.text('Loại phản ứng'), findsNothing);
    expect(find.text('Lưu thông tin dị ứng'), findsOneWidget);
  });

  testWidgets('anonymous /preferences redirects to login', (tester) async {
    final session = await _anonymousSession();
    final router = AppRouter(
      session,
      preferencesController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/preferences');
    await tester.pumpAndSettle();

    expect(find.text('Chào mừng bạn quay lại'), findsOneWidget);
  });

  testWidgets('intended /preferences is restored after login', (tester) async {
    final session = await _anonymousSession();
    final router = AppRouter(
      session,
      preferencesController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/preferences');
    await tester.pumpAndSettle();

    final emailField = _fieldWithLabel('Email');
    final passwordField = _fieldWithLabel('Mật khẩu');
    expect(emailField, findsOneWidget);
    expect(passwordField, findsOneWidget);
    await tester.enterText(emailField, 'user@example.test');
    await tester.enterText(passwordField, 'Password123!');
    await tester.tap(find.widgetWithText(FilledButton, 'Đăng nhập'));
    await tester.pumpAndSettle();

    expect(_dietarySectionTitle(), findsOneWidget);
  });

  testWidgets('external intended preference routes are rejected', (
    tester,
  ) async {
    final session = await _authenticatedSession();
    final router = AppRouter(
      session,
      preferencesController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));

    for (final target in [
      'https://evil.example/preferences',
      '//evil.example/preferences',
      'https://user@evil.example/preferences',
      'javascript:anything',
      '/preferences/unknown',
    ]) {
      router.go('/auth/login?from=${Uri.encodeComponent(target)}');
      await tester.pumpAndSettle();
      expect(
        router.routerDelegate.currentConfiguration.uri.toString(),
        '/catalog/foods',
        reason: 'rejected intended route: $target',
      );
    }
  });

  testWidgets('Preferences navigation opens /preferences', (tester) async {
    final session = await _authenticatedSession();
    final router = AppRouter(
      session,
      preferencesController: _controller(),
    ).router;
    await tester.pumpWidget(_routerApp(router));
    await tester.pumpAndSettle();

    final preferencesNavigation = find.text('Sở thích ăn uống');
    expect(preferencesNavigation, findsOneWidget);
    await tester.tap(preferencesNavigation);
    await tester.pumpAndSettle();

    expect(_dietarySectionTitle(), findsOneWidget);
  });

  testWidgets('existing dietary preferences render selected', (tester) async {
    final controller = _controller(
      selectedDietaryPreferences: const [_veganSelection],
    );
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    final veganTile = tester.widget<FilterChip>(
      find.byKey(const ValueKey('dietary-VEGAN')),
    );
    expect(veganTile.selected, isTrue);
  });

  testWidgets('existing allergens render selected', (tester) async {
    final controller = _controller(selectedAllergens: const [_peanutSelection]);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    final peanutTile = tester.widget<InputChip>(
      find.byKey(const ValueKey('allergen-PEANUT')),
    );
    expect(peanutTile.onDeleted, isNotNull);
    expect(find.byKey(const ValueKey('reaction-PEANUT')), findsNothing);
    expect(
      find.byKey(const ValueKey('allergen-editor-PEANUT')),
      findsOneWidget,
    );
  });

  testWidgets(
    'allergen typing resolves codes, prevents duplicates, and removes chips',
    (tester) async {
      final controller = _controller(selectedAllergens: const []);
      await tester.pumpWidget(_pageApp(controller));
      await tester.pumpAndSettle();

      final search = find.byKey(const ValueKey('allergen-search'));
      await tester.enterText(search, 'Đậu');
      await tester.pumpAndSettle();
      expect(find.text('Đậu phộng'), findsOneWidget);
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      expect(controller.state.selectedAllergenCodes, ['PEANUT']);
      expect(find.byKey(const ValueKey('allergen-PEANUT')), findsOneWidget);

      await tester.enterText(search, 'Đậu');
      await tester.pumpAndSettle();
      expect(
        find.text('Không tìm thấy chất gây dị ứng này trong danh mục.'),
        findsOneWidget,
      );
      expect(controller.state.selectedAllergenCodes, ['PEANUT']);
      tester
          .widget<InputChip>(find.byKey(const ValueKey('allergen-PEANUT')))
          .onDeleted!();
      await tester.pump();
      expect(controller.state.selectedAllergenCodes, isEmpty);
    },
  );

  testWidgets('dietary selections can be added and removed', (tester) async {
    final controller = _controller(selectedDietaryPreferences: const []);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    final veganTile = find.byKey(const ValueKey('dietary-VEGAN'));
    await tester.tap(veganTile);
    await tester.pump();
    expect(controller.state.selectedDietaryCodes, ['VEGAN']);

    await tester.tap(veganTile);
    await tester.pump();
    expect(controller.state.selectedDietaryCodes, isEmpty);
  });

  testWidgets('empty dietary selection can be saved', (tester) async {
    final repository = FakePreferencesRepository(
      selectedDietaryPreferences: const [_veganSelection],
    );
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('dietary-VEGAN')));
    await tester.pump();

    await _tapAfterScroll(
      tester,
      find.byKey(const ValueKey('save-dietary-preferences')),
    );
    await tester.pumpAndSettle();

    expect(repository.dietaryUpdateCalls, 1);
    expect(repository.lastDietaryCodes, isEmpty);
    expect(find.text('Đã lưu sở thích ăn uống.'), findsOneWidget);
  });

  testWidgets('allergen selection supports reaction and optional note', (
    tester,
  ) async {
    final repository = FakePreferencesRepository(selectedAllergens: const []);
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('allergen-search')),
      'Đậu',
    );
    await tester.pump();
    await tester.tap(find.text('Đậu phộng').last);
    await tester.pump();
    expect(controller.state.selectedAllergenCodes, ['PEANUT']);
    final editor = find.byKey(const ValueKey('allergen-editor-PEANUT'));
    await tester.ensureVisible(editor);
    await tester.tap(editor);
    await tester.pumpAndSettle();

    final reaction = find.byKey(const ValueKey('reaction-PEANUT'));
    await tester.ensureVisible(reaction);
    await tester.pump();
    await tester.tap(reaction);
    await tester.pump();
    await tester.tap(find.text('Dị ứng').last);
    await tester.pump();

    final note = find.byKey(const ValueKey('note-PEANUT'));
    await tester.ensureVisible(note);
    await tester.pump();
    await tester.enterText(note, 'Avoid cross-contact');
    await tester.pump();

    await _tapAfterScroll(tester, find.byKey(const ValueKey('save-allergens')));
    await tester.pumpAndSettle();

    expect(repository.allergenUpdateCalls, 1);
    expect(repository.lastAllergenSelections, hasLength(1));
    expect(
      repository.lastAllergenSelections!.single.reactionKind,
      ReactionKind.allergy,
    );
    expect(
      repository.lastAllergenSelections!.single.note,
      'Avoid cross-contact',
    );
    expect(find.text('Đã lưu thông tin dị ứng.'), findsOneWidget);
  });

  testWidgets('typing shrimp resolves the supported crustacean code', (
    tester,
  ) async {
    final repository = FakePreferencesRepository(
      allergenReferences: const [
        AllergenReference(
          code: 'CRUSTACEANS',
          displayName: 'Crustaceans',
          description: 'Crustaceans and their products.',
          displayOrder: 1,
        ),
      ],
      selectedAllergens: const [],
    );
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('allergen-search')),
      'Tôm',
    );
    await tester.pump();
    await tester.tap(find.text('Động vật giáp xác').last);
    await tester.pump();

    expect(controller.state.selectedAllergenCodes, ['CRUSTACEANS']);
    expect(find.byKey(const ValueKey('allergen-CRUSTACEANS')), findsOneWidget);
  });

  testWidgets('256-character allergen note blocks save', (tester) async {
    final repository = FakePreferencesRepository(selectedAllergens: const []);
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('allergen-search')),
      'Đậu',
    );
    await tester.pump();
    await tester.tap(find.text('Đậu phộng').last);
    await tester.pump();
    final editor = find.byKey(const ValueKey('allergen-editor-PEANUT'));
    await tester.ensureVisible(editor);
    await tester.tap(editor);
    await tester.pumpAndSettle();

    final note = find.byKey(const ValueKey('note-PEANUT'));
    await tester.ensureVisible(note);
    await tester.pump();
    await tester.enterText(note, 'x' * 256);
    expect(tester.widget<TextFormField>(note).controller!.text, 'x' * 256);

    final saveFinder = find.byKey(const ValueKey('save-allergens'));
    final saveButton = tester.widget<TextButton>(saveFinder);
    expect(saveButton.onPressed, isNotNull);
    saveButton.onPressed!();
    await tester.pump();

    expect(repository.allergenUpdateCalls, 0);
    expect(find.textContaining('255 ký tự'), findsOneWidget);
  });

  testWidgets('allergen safety message and empty clear save are available', (
    tester,
  ) async {
    final repository = FakePreferencesRepository(
      selectedAllergens: const [_peanutSelection],
    );
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    expect(
      find.textContaining(
        'Các chất gây dị ứng đã chọn sẽ luôn bị loại khỏi gợi ý bữa ăn.',
      ),
      findsOneWidget,
    );
    final allergenChip = tester.widget<InputChip>(
      find.byKey(const ValueKey('allergen-PEANUT')),
    );
    allergenChip.onDeleted!();
    await tester.pump();
    await _tapAfterScroll(tester, find.byKey(const ValueKey('save-allergens')));
    await tester.pumpAndSettle();

    expect(repository.lastAllergenSelections, isEmpty);
  });

  testWidgets('preferences page remains scrollable at narrow width', (
    tester,
  ) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.binding.setSurfaceSize(const Size(360, 800));
    await tester.pumpWidget(_pageApp(_controller(selectedAllergens: const [])));
    await tester.pumpAndSettle();

    final scrollView = find.byKey(const ValueKey('preferences-scroll'));
    final saveButton = find.byKey(const ValueKey('save-allergens'));
    expect(scrollView, findsOneWidget);
    expect(saveButton, findsOneWidget);
    await tester.ensureVisible(saveButton);
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty preferences state has no duplicate string keys', (
    tester,
  ) async {
    await tester.pumpWidget(
      _pageApp(
        _controller(
          selectedDietaryPreferences: const [],
          selectedAllergens: const [],
        ),
      ),
    );
    await tester.pumpAndSettle();

    final values = <String>{};
    for (final widget in tester.allWidgets) {
      final key = widget.key;
      if (key is ValueKey<String>) {
        expect(values.add(key.value), isTrue, reason: 'duplicate key: $key');
      }
    }
  });
}

Future<void> _tapAfterScroll(WidgetTester tester, Finder target) async {
  expect(target, findsOneWidget);
  final scrollView = find.byKey(const ValueKey('preferences-scroll'));
  expect(scrollView, findsOneWidget);
  await tester.dragUntilVisible(target, scrollView, const Offset(0, -200));
  await tester.pump();
  await tester.tap(target);
  await tester.pump();
}

Finder _fieldWithLabel(String label) =>
    find.ancestor(of: find.text(label), matching: find.byType(TextFormField));

Finder _dietarySectionTitle() => find.descendant(
  of: find.byKey(const ValueKey('dietary-preferences-section')),
  matching: find.text('Chế độ ăn'),
);

Widget _routerApp(GoRouter router) => MaterialApp.router(routerConfig: router);

Widget _pageApp(PreferencesController controller) => MaterialApp(
  home: PreferencesPage(
    sessionController: _pageSession(),
    preferencesController: controller,
  ),
);

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

SessionController _pageSession() => SessionController(
  authRepository: FakeAuthRepository(),
  refreshTokenStore: NoRefreshTokenStore(),
  isWeb: true,
);

PreferencesController _controller({
  FakePreferencesRepository? repository,
  List<SelectedDietaryPreference>? selectedDietaryPreferences,
  List<SelectedAllergen>? selectedAllergens,
}) {
  final preferencesRepository =
      repository ??
      FakePreferencesRepository(
        selectedDietaryPreferences: selectedDietaryPreferences,
        selectedAllergens: selectedAllergens,
      );
  return PreferencesController(repository: preferencesRepository);
}

final class FakePreferencesRepository implements PreferencesRepository {
  FakePreferencesRepository({
    List<DietaryPreferenceReference>? dietaryReferences,
    List<SelectedDietaryPreference>? selectedDietaryPreferences,
    List<AllergenReference>? allergenReferences,
    List<SelectedAllergen>? selectedAllergens,
    this.dietaryError,
    this.allergenError,
  }) : dietaryReferences = dietaryReferences ?? _dietaryReferences,
       selectedDietaryPreferences =
           selectedDietaryPreferences ?? _selectedDietaryPreferences,
       allergenReferences = allergenReferences ?? _allergenReferences,
       selectedAllergens = selectedAllergens ?? _selectedAllergens;

  final List<DietaryPreferenceReference> dietaryReferences;
  final List<SelectedDietaryPreference> selectedDietaryPreferences;
  final List<AllergenReference> allergenReferences;
  final List<SelectedAllergen> selectedAllergens;
  final Object? dietaryError;
  final Object? allergenError;
  var dietaryUpdateCalls = 0;
  var allergenUpdateCalls = 0;
  List<String>? lastDietaryCodes;
  List<AllergenSelection>? lastAllergenSelections;

  @override
  Future<List<DietaryPreferenceReference>>
  getDietaryPreferenceReferences() async => dietaryReferences;

  @override
  Future<List<SelectedDietaryPreference>>
  getSelectedDietaryPreferences() async => selectedDietaryPreferences;

  @override
  Future<List<SelectedDietaryPreference>> replaceDietaryPreferences(
    List<String> codes,
  ) async {
    dietaryUpdateCalls++;
    lastDietaryCodes = [...codes];
    if (dietaryError != null) throw dietaryError!;
    return [
      for (final code in codes)
        for (final reference in dietaryReferences)
          if (reference.code == code)
            SelectedDietaryPreference(
              code: reference.code,
              displayName: reference.displayName,
              description: reference.description,
              isExclusionary: reference.isExclusionary,
            ),
    ];
  }

  @override
  Future<List<AllergenReference>> getAllergenReferences() async =>
      allergenReferences;

  @override
  Future<List<SelectedAllergen>> getSelectedAllergens() async =>
      selectedAllergens;

  @override
  Future<List<SelectedAllergen>> replaceAllergens(
    List<AllergenSelection> selections,
  ) async {
    allergenUpdateCalls++;
    lastAllergenSelections = [...selections];
    if (allergenError != null) throw allergenError!;
    return [
      for (final selection in selections)
        for (final reference in allergenReferences)
          if (reference.code == selection.allergen)
            SelectedAllergen(
              allergen: reference.code,
              displayName: reference.displayName,
              description: reference.description,
              reactionKind: selection.reactionKind,
              note: selection.note?.trim().isEmpty ?? true
                  ? null
                  : selection.note!.trim(),
            ),
    ];
  }
}

const _dietaryReferences = [
  DietaryPreferenceReference(
    code: 'VEGAN',
    displayName: 'Vegan',
    description: 'Plant-based meals.',
    isExclusionary: true,
    displayOrder: 1,
  ),
  DietaryPreferenceReference(
    code: 'LOW_CARB',
    displayName: 'Low carbohydrate',
    description: 'Limit carbohydrate-heavy choices.',
    isExclusionary: true,
    displayOrder: 2,
  ),
];

const _selectedDietaryPreferences = [_veganSelection];

const _veganSelection = SelectedDietaryPreference(
  code: 'VEGAN',
  displayName: 'Vegan',
  description: 'Plant-based meals.',
  isExclusionary: true,
);

const _allergenReferences = [
  AllergenReference(
    code: 'PEANUT',
    displayName: 'Peanuts',
    description: 'Peanuts and peanut products.',
    displayOrder: 1,
  ),
  AllergenReference(
    code: 'TREE_NUT',
    displayName: 'Tree nuts',
    description: 'Tree nuts and products.',
    displayOrder: 2,
  ),
];

const _selectedAllergens = [_peanutSelection];

const _peanutSelection = SelectedAllergen(
  allergen: 'PEANUT',
  displayName: 'Peanuts',
  description: 'Peanuts and peanut products.',
  reactionKind: ReactionKind.allergy,
  note: 'Avoid cross-contact.',
);
