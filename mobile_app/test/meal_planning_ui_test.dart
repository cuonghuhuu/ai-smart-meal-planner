import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_controller.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';
import 'package:smart_meal_planner/features/meal_planning/presentation/meal_planning_page.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_meal_planning_repository.dart';

void main() {
  testWidgets('planning controls fit mobile, tablet, and desktop widths', (
    tester,
  ) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    for (final size in const [
      Size(390, 844),
      Size(768, 1024),
      Size(1366, 900),
    ]) {
      await tester.binding.setSurfaceSize(size);
      await _showPage(tester, FakeMealPlanningRepository());
      await tester.ensureVisible(
        find.byKey(const ValueKey('meal-plan-generate')),
      );
      await tester.pump();
      expect(tester.takeException(), isNull, reason: 'viewport $size');
    }
  });

  testWidgets('authenticated navigation opens protected Meal Planning route', (
    tester,
  ) async {
    final session = await _session(authenticated: true);
    final controller = MealPlanningController(
      repository: FakeMealPlanningRepository(),
    );
    final router = AppRouter(
      session,
      mealPlanningController: controller,
    ).router;
    addTearDown(() {
      router.dispose();
      controller.dispose();
    });
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await _pumpAsync(tester);
    await tester.tap(find.text(AppStrings.mealPlanning).first);
    await _pumpAsync(tester);

    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '/meal-planning',
    );
    expect(find.byKey(const ValueKey('meal-planning-title')), findsOneWidget);
  });

  testWidgets('anonymous route redirects to login', (tester) async {
    final session = await _session(authenticated: false);
    final controller = MealPlanningController(
      repository: FakeMealPlanningRepository(),
    );
    final router = AppRouter(
      session,
      mealPlanningController: controller,
    ).router;
    addTearDown(() {
      router.dispose();
      controller.dispose();
    });
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    router.go('/meal-planning');
    await _pumpAsync(tester);

    expect(router.routerDelegate.currentConfiguration.uri.path, '/auth/login');
    await tester.enterText(
      find.byType(TextFormField).at(0),
      'user@example.test',
    );
    await tester.enterText(find.byType(TextFormField).at(1), 'Password123!');
    await tester.tap(find.widgetWithText(FilledButton, AppStrings.signIn));
    await _pumpAsync(tester);
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '/meal-planning',
    );
  });

  testWidgets('date picker and selected days and slots are available', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository();
    await _showPage(tester, repository);
    await tester.tap(find.byKey(const ValueKey('meal-plan-start-date')));
    await tester.pumpAndSettle();
    expect(find.byType(DatePickerDialog), findsOneWidget);
    final selectedDate = DateUtils.dateOnly(DateTime.now())
        .add(const Duration(days: 1));
    Navigator.of(tester.element(find.byType(DatePickerDialog)))
        .pop(selectedDate);
    await tester.pumpAndSettle();

    final days = find.byKey(const ValueKey('meal-plan-days'));
    await tester.ensureVisible(days);
    await tester.tap(days);
    await tester.pumpAndSettle();
    await tester.tap(find.text('2').last);
    await tester.pumpAndSettle();
    for (final slot in ['BREAKFAST', 'LUNCH']) {
      final chip = find.byKey(ValueKey('meal-plan-slot-$slot'));
      await tester.ensureVisible(chip);
      await tester.tap(chip);
      await tester.pump();
    }
    final evening = find.byKey(const ValueKey('meal-plan-slot-EVENING_SNACK'));
    await tester.ensureVisible(evening);
    await tester.tap(evening);
    await tester.pump();
    await _tapGenerate(tester);

    expect(repository.requests.single.days, 2);
    expect(repository.requests.single.startDate, selectedDate);
    expect(repository.requests.single.requestedMealSlots, [
      MealSlotCode.dinner,
      MealSlotCode.eveningSnack,
    ]);
  });

  testWidgets('valid form sends codes and renders SUCCEEDED entries', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository();
    await _showPage(tester, repository);
    await tester.enterText(
      find.byKey(const ValueKey('meal-plan-servings')),
      '2,5',
    );
    await tester.enterText(
      find.byKey(const ValueKey('meal-plan-max-minutes')),
      '45',
    );
    await _tapGenerate(tester);

    expect(repository.requests, hasLength(1));
    final request = repository.requests.single;
    expect(
      formatMealPlanDate(request.startDate),
      formatMealPlanDate(DateUtils.dateOnly(DateTime.now())),
    );
    expect(request.days, 7);
    expect(request.requestedMealSlots.map((slot) => slot.wireValue), [
      'BREAKFAST',
      'LUNCH',
      'DINNER',
    ]);
    expect(request.defaultServings, 2.5);
    expect(request.maxMinutesPerMeal, 45);
    expect(repository.readIds, [planId]);
    expect(find.byKey(const ValueKey('meal-plan-result')), findsOneWidget);
    expect(find.text('Oatmeal with Banana'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('meal-plan-result')),
        matching: find.textContaining(AppStrings.mealPlanBreakfast),
      ),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('meal-plan-day-2026-10-05')),
      findsOneWidget,
    );
  });

  testWidgets('DEGRADED displays filled entry and fallback for gap', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository();
    repository.onGenerate = (_) async =>
        generated(status: MealPlanGenerationStatus.degraded);
    repository.onGetPlan = (_) async =>
        persistedPlan(status: MealPlanGenerationStatus.degraded);
    await _showPage(tester, repository);
    await _tapGenerate(tester);

    expect(find.text(AppStrings.mealPlanDegraded), findsOneWidget);
    expect(find.text('Oatmeal with Banana'), findsOneWidget);
    expect(find.text(AppStrings.mealPlanNoEligibleRecipe), findsOneWidget);
    expect(
      find.byKey(const ValueKey('meal-plan-day-2026-10-06')),
      findsOneWidget,
    );
  });

  testWidgets('DEGRADED displays supplied gap explanation', (tester) async {
    final repository = FakeMealPlanningRepository();
    repository.onGenerate = (_) async =>
        generated(status: MealPlanGenerationStatus.degraded);
    repository.onGetPlan = (_) async => persistedPlan(
      status: MealPlanGenerationStatus.degraded,
      explanation: 'No dinner recipe fits.',
    );
    await _showPage(tester, repository);
    await _tapGenerate(tester);
    expect(find.text('No dinner recipe fits.'), findsOneWidget);
  });

  testWidgets('INFEASIBLE shows guidance and never sends GET', (tester) async {
    final repository = FakeMealPlanningRepository()
      ..onGenerate = (_) async =>
          generated(status: MealPlanGenerationStatus.infeasible);
    await _showPage(tester, repository);
    await _tapGenerate(tester);

    expect(find.byKey(const ValueKey('meal-plan-infeasible')), findsOneWidget);
    expect(find.text(AppStrings.mealPlanInfeasible), findsOneWidget);
    expect(repository.readIds, isEmpty);
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('meal-plan-generate')),
          )
          .onPressed,
      isNotNull,
    );
  });

  testWidgets('read failure shows retry; retry performs GET only', (
    tester,
  ) async {
    var reads = 0;
    final repository = FakeMealPlanningRepository()
      ..onGetPlan = (_) async {
        reads++;
        if (reads == 1) throw const ApiHttpException(503);
        return persistedPlan();
      };
    await _showPage(tester, repository);
    await _tapGenerate(tester);

    expect(find.byKey(const ValueKey('meal-plan-error')), findsOneWidget);
    expect(find.text(AppStrings.mealPlanReadFailed), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('meal-plan-generate')),
          )
          .onPressed,
      isNull,
    );
    final retry = find.byKey(const ValueKey('meal-plan-retry-load'));
    await tester.ensureVisible(retry);
    await tester.tap(retry);
    await _pumpAsync(tester);

    expect(repository.requests, hasLength(1));
    expect(repository.readIds, [planId, planId]);
    expect(find.text('Oatmeal with Banana'), findsOneWidget);
  });

  testWidgets('busy Generate button is disabled and loading messages change', (
    tester,
  ) async {
    final post = Completer<MealPlanGenerationResponse>();
    final read = Completer<PersistedMealPlan>();
    final repository = FakeMealPlanningRepository();
    repository.onGenerate = (_) => post.future;
    repository.onGetPlan = (_) => read.future;
    await _showPage(tester, repository);
    await _tapGenerate(tester, waitForResult: false);

    expect(find.text(AppStrings.mealPlanGenerating), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('meal-plan-generate')),
          )
          .onPressed,
      isNull,
    );
    expect(repository.requests, hasLength(1));
    post.complete(generated());
    await _pumpAsync(tester);
    expect(find.text(AppStrings.mealPlanLoading), findsOneWidget);
    expect(repository.readIds, [planId]);
    read.complete(persistedPlan());
    await _pumpAsync(tester);
    expect(find.text('Oatmeal with Banana'), findsOneWidget);
  });

  testWidgets('empty slots and invalid numeric fields block POST', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository();
    await _showPage(tester, repository);
    for (final slot in ['BREAKFAST', 'LUNCH', 'DINNER']) {
      final chip = find.byKey(ValueKey('meal-plan-slot-$slot'));
      await tester.ensureVisible(chip);
      await tester.tap(chip);
      await tester.pump();
    }
    await tester.enterText(
      find.byKey(const ValueKey('meal-plan-servings')),
      '0',
    );
    await tester.enterText(
      find.byKey(const ValueKey('meal-plan-max-minutes')),
      '1441',
    );
    await _tapGenerate(tester);

    expect(find.text(AppStrings.mealPlanSlotsRequired), findsOneWidget);
    expect(find.text(AppStrings.mealPlanServingsInvalid), findsOneWidget);
    expect(find.text(AppStrings.mealPlanMaxMinutesInvalid), findsOneWidget);
    expect(repository.requests, isEmpty);
  });

  testWidgets('shopping list loads only on tap and shows quantities', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository();
    await _showPage(tester, repository);
    await _tapGenerate(tester);

    expect(find.byKey(const ValueKey('shopping-list-load')), findsOneWidget);
    expect(repository.shoppingListReadIds, isEmpty);

    await _tapShoppingListLoad(tester);

    expect(repository.shoppingListReadIds, [planId]);
    expect(repository.requests, hasLength(1));
    expect(find.byKey(const ValueKey('meal-plan-result')), findsOneWidget);
    expect(find.byKey(const ValueKey('shopping-list-result')), findsOneWidget);
    expect(find.text(AppStrings.shoppingListTitle), findsOneWidget);
    expect(find.text('Chicken'), findsOneWidget);
    expect(
      find.text('${AppStrings.shoppingListRequired}: 800 g'),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.shoppingListPantryCovered}: 550 g'),
      findsOneWidget,
    );
    expect(find.text('${AppStrings.shoppingListToBuy}: 250 g'), findsOneWidget);
    expect(find.text('Salt'), findsOneWidget);
    expect(find.text(AppStrings.shoppingListUnquantifiedTitle), findsOneWidget);
    expect(find.text(AppStrings.shoppingListAsNeeded), findsOneWidget);
    expect(find.textContaining('800.0 g'), findsNothing);
    expect(find.textContaining('550.0000 g'), findsNothing);
    expect(find.textContaining(chickenId), findsNothing);
    expect(find.textContaining(saltId), findsNothing);
  });

  testWidgets('shopping-list loading keeps the plan visible', (tester) async {
    final pending = Completer<MealPlanShoppingList>();
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) => pending.future;
    await _showPage(tester, repository);
    await _tapGenerate(tester);

    await _tapShoppingListLoad(tester, waitForResult: false);

    expect(repository.shoppingListReadIds, [planId]);
    expect(find.byKey(const ValueKey('meal-plan-result')), findsOneWidget);
    expect(find.text('Oatmeal with Banana'), findsOneWidget);
    expect(find.byKey(const ValueKey('shopping-list-loading')), findsOneWidget);
    expect(find.text(AppStrings.shoppingListLoading), findsOneWidget);
    expect(find.byKey(const ValueKey('shopping-list-load')), findsNothing);

    pending.complete(shoppingList());
    await _pumpAsync(tester);
    expect(find.byKey(const ValueKey('shopping-list-result')), findsOneWidget);
    expect(repository.shoppingListReadIds, [planId]);
  });

  testWidgets('shopping-list error keeps the plan and retry reads only list', (
    tester,
  ) async {
    var reads = 0;
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) async {
        reads++;
        if (reads == 1) {
          throw const ApiHttpException(
            503,
            problem: ApiProblem(
              status: 503,
              code: 'SHOPPING_LIST_FAILED',
              detail: 'private backend detail',
            ),
          );
        }
        return shoppingList();
      };
    await _showPage(tester, repository);
    await _tapGenerate(tester);
    await _tapShoppingListLoad(tester);

    expect(find.byKey(const ValueKey('meal-plan-result')), findsOneWidget);
    expect(find.text('Oatmeal with Banana'), findsOneWidget);
    expect(find.byKey(const ValueKey('shopping-list-error')), findsOneWidget);
    expect(find.text(AppStrings.shoppingListLoadFailed), findsOneWidget);
    expect(find.textContaining('private backend detail'), findsNothing);

    final retry = find.byKey(const ValueKey('shopping-list-retry'));
    expect(retry, findsOneWidget);
    await tester.ensureVisible(retry);
    await tester.tap(retry);
    await _pumpAsync(tester);

    expect(repository.shoppingListReadIds, [planId, planId]);
    expect(repository.requests, hasLength(1));
    expect(repository.readIds, [planId]);
    expect(find.byKey(const ValueKey('meal-plan-result')), findsOneWidget);
    expect(find.byKey(const ValueKey('shopping-list-result')), findsOneWidget);
  });

  testWidgets('DEGRADED plan shows the shopping-list coverage hint', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository();
    repository.onGenerate = (_) async =>
        generated(status: MealPlanGenerationStatus.degraded);
    repository.onGetPlan = (_) async =>
        persistedPlan(status: MealPlanGenerationStatus.degraded);
    repository.onGetShoppingList = (_) async =>
        shoppingList(status: MealPlanGenerationStatus.degraded);
    await _showPage(tester, repository);
    await _tapGenerate(tester);
    await _tapShoppingListLoad(tester);

    expect(repository.shoppingListReadIds, [planId]);
    expect(find.byKey(const ValueKey('shopping-list-result')), findsOneWidget);
    expect(find.text(AppStrings.shoppingListDegradedHint), findsOneWidget);
    expect(find.text('Chicken'), findsOneWidget);
  });

  testWidgets('empty shopping list shows a meaningful result', (tester) async {
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) async => MealPlanShoppingList(
        mealPlanPublicId: planId,
        status: MealPlanGenerationStatus.succeeded,
        items: const [],
        unquantifiedItems: const [],
      );
    await _showPage(tester, repository);
    await _tapGenerate(tester);
    await _tapShoppingListLoad(tester);

    expect(find.byKey(const ValueKey('shopping-list-result')), findsOneWidget);
    expect(find.text(AppStrings.shoppingListTitle), findsOneWidget);
    expect(find.text(AppStrings.shoppingListNoQuantifiedItems), findsOneWidget);
    expect(find.byKey(const ValueKey('meal-plan-result')), findsOneWidget);
  });

  testWidgets('fractional shopping quantities omit trailing zeroes', (
    tester,
  ) async {
    final repository = FakeMealPlanningRepository()
      ..onGetShoppingList = (_) async => MealPlanShoppingList(
        mealPlanPublicId: planId,
        status: MealPlanGenerationStatus.succeeded,
        items: const [
          ShoppingListItem(
            ingredientPublicId: chickenId,
            ingredientCode: 'chicken',
            ingredientDisplayName: 'Chicken',
            requiredQuantity: 12.34,
            pantryCoveredQuantity: 0.25,
            quantityToBuy: 12.09,
            unitCode: 'g',
          ),
        ],
        unquantifiedItems: const [],
      );
    await _showPage(tester, repository);
    await _tapGenerate(tester);
    await _tapShoppingListLoad(tester);

    expect(
      find.text('${AppStrings.shoppingListRequired}: 12.34 g'),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.shoppingListPantryCovered}: 0.25 g'),
      findsOneWidget,
    );
    expect(
      find.text('${AppStrings.shoppingListToBuy}: 12.09 g'),
      findsOneWidget,
    );
    expect(find.textContaining('12.3400 g'), findsNothing);
  });

  testWidgets('slot and reason codes have readable labels', (tester) async {
    for (final slot in MealSlotCode.values) {
      expect(mealSlotLabel(slot), isNot(slot.wireValue));
      expect(mealSlotLabel(slot), isNotEmpty);
    }
    for (final reason in UnfilledSlotReasonCode.values) {
      expect(unfilledReasonLabel(reason), isNot(reason.wireValue));
      expect(unfilledReasonLabel(reason), isNotEmpty);
    }
  });
}

Future<void> _showPage(
  WidgetTester tester,
  FakeMealPlanningRepository repository,
) async {
  final controller = MealPlanningController(repository: repository);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: MealPlanningPage(
        sessionController: await _session(authenticated: true),
        mealPlanningController: controller,
      ),
    ),
  );
  await _pumpAsync(tester);
}

Future<void> _tapGenerate(
  WidgetTester tester, {
  bool waitForResult = true,
}) async {
  final generate = find.byKey(const ValueKey('meal-plan-generate'));
  await tester.ensureVisible(generate);
  await tester.tap(generate);
  if (waitForResult) {
    await _pumpAsync(tester);
  } else {
    await tester.pump();
  }
}

Future<void> _tapShoppingListLoad(
  WidgetTester tester, {
  bool waitForResult = true,
}) async {
  final load = find.byKey(const ValueKey('shopping-list-load'));
  await tester.ensureVisible(load);
  await tester.tap(load);
  if (waitForResult) {
    await _pumpAsync(tester);
  } else {
    await tester.pump();
  }
}

Future<void> _pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 100));
}

Future<SessionController> _session({required bool authenticated}) async {
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  if (authenticated) {
    await session.login('user@example.test', 'Password123!');
  } else {
    await session.bootstrap();
  }
  return session;
}
