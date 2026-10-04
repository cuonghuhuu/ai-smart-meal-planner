import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_pantry_repository.dart';

void main() {
  for (final status in PantryItemStatus.values) {
    testWidgets('$status shows only permitted quantity actions', (
      tester,
    ) async {
      final item = pantryItem(
        status: status,
        quantityInitial: '10',
        quantityRemaining:
            status == PantryItemStatus.available ||
                status == PantryItemStatus.reserved
            ? '5'
            : '0',
      );
      final fixture = await _fixture(item);
      await tester.pumpWidget(fixture.app);
      fixture.router.go('/pantry/$testPantryId');
      await _pump(tester);
      expect(
        find.byKey(const ValueKey('pantry-adjust')),
        status == PantryItemStatus.available ? findsOneWidget : findsNothing,
      );
      expect(
        find.byKey(const ValueKey('pantry-consume')),
        status == PantryItemStatus.available ? findsOneWidget : findsNothing,
      );
      expect(
        find.byKey(const ValueKey('pantry-discard')),
        status == PantryItemStatus.available ||
                status == PantryItemStatus.reserved
            ? findsOneWidget
            : findsNothing,
      );
    });
  }

  testWidgets(
    'adjust validates exact bounds, keeps draft, and normalizes note',
    (tester) async {
      final fixture = await _fixture(
        pantryItem(quantityInitial: '10', quantityRemaining: '5'),
      );
      await tester.pumpWidget(fixture.app);
      fixture.router.go('/pantry/$testPantryId');
      await _pump(tester);
      await tester.tap(find.byKey(const ValueKey('pantry-adjust')));
      await tester.pumpAndSettle();

      for (final invalid in ['0', '-5', '5.0001', '1.23456']) {
        await tester.enterText(
          find.byKey(const ValueKey('pantry-action-quantity')),
          invalid,
        );
        await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
        await tester.pump();
        expect(fixture.repository.adjustRequests, isEmpty);
        expect(find.text(AppStrings.pantryAdjustmentInvalid), findsWidgets);
      }

      fixture.repository.adjustError = const ApiTransportException(
        ApiTransportFailureKind.network,
      );
      await tester.enterText(
        find.byKey(const ValueKey('pantry-action-quantity')),
        '-0.0001',
      );
      await tester.enterText(
        find.byKey(const ValueKey('pantry-action-note')),
        '  correction  ',
      );
      await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
      await _pump(tester);
      expect(find.byKey(const ValueKey('pantry-action-error')), findsOneWidget);
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const ValueKey('pantry-action-quantity')),
            )
            .controller!
            .text,
        '-0.0001',
      );
      fixture.repository.adjustError = null;
      fixture.repository.onAdjust = (_, _) async => pantryItem(
        quantityInitial: '10',
        quantityRemaining: '4.9999',
        note: 'metadata',
      );
      await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
      await tester.pumpAndSettle();
      expect(fixture.repository.adjustRequests, hasLength(2));
      expect(fixture.repository.adjustRequests.last.$2.toJson(), {
        'quantityDelta': -0.0001,
        'note': 'correction',
      });
      expect(fixture.controller.detailState.item?.note, 'metadata');
      expect(
        fixture.controller.detailState.item?.quantityRemaining.toString(),
        '4.9999',
      );
    },
  );

  testWidgets('consume accepts exact remaining and adopts closed status', (
    tester,
  ) async {
    final fixture = await _fixture(
      pantryItem(quantityInitial: '10', quantityRemaining: '5'),
    );
    fixture.repository.onConsume = (_, _) async => pantryItem(
      quantityInitial: '10',
      quantityRemaining: '0',
      status: PantryItemStatus.consumed,
      closedAt: DateTime(2026, 1, 2),
    );
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId');
    await _pump(tester);
    await tester.tap(find.byKey(const ValueKey('pantry-consume')));
    await tester.pumpAndSettle();
    expect(find.textContaining('5 bao'), findsWidgets);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-action-quantity')),
      '5.0001',
    );
    await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
    await tester.pump();
    expect(fixture.repository.consumeRequests, isEmpty);
    await tester.enterText(
      find.byKey(const ValueKey('pantry-action-quantity')),
      '5',
    );
    await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
    await tester.pumpAndSettle();
    expect(fixture.repository.consumeRequests, hasLength(1));
    expect(
      fixture.controller.detailState.item?.status,
      PantryItemStatus.consumed,
    );
    expect(find.byKey(const ValueKey('pantry-consume')), findsNothing);
  });

  testWidgets('consume validates note and preserves draft after failure', (
    tester,
  ) async {
    final fixture = await _fixture(
      pantryItem(quantityInitial: '10', quantityRemaining: '5'),
    );
    fixture.repository.consumeError = const ApiTransportException(
      ApiTransportFailureKind.network,
    );
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId');
    await _pump(tester);
    await tester.tap(find.byKey(const ValueKey('pantry-consume')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('pantry-action-quantity')),
      '1.2345',
    );
    await tester.enterText(
      find.byKey(const ValueKey('pantry-action-note')),
      'x' * 256,
    );
    await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
    await tester.pump();
    expect(fixture.repository.consumeRequests, isEmpty);
    expect(find.text(AppStrings.pantryNoteTooLong), findsWidgets);

    await tester.enterText(
      find.byKey(const ValueKey('pantry-action-note')),
      '  meal prep  ',
    );
    await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
    await _pump(tester);
    expect(find.byKey(const ValueKey('pantry-action-error')), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(
            find.byKey(const ValueKey('pantry-action-quantity')),
          )
          .controller!
          .text,
      '1.2345',
    );
    fixture.repository.consumeError = null;
    fixture.repository.onConsume = (_, _) async =>
        pantryItem(quantityInitial: '10', quantityRemaining: '3.7655');
    await tester.tap(find.byKey(const ValueKey('pantry-action-submit')));
    await tester.pumpAndSettle();
    expect(fixture.repository.consumeRequests, hasLength(2));
    expect(
      fixture.repository.consumeRequests.last.$2.toJson()['note'],
      'meal prep',
    );
    expect(
      fixture.controller.detailState.item?.quantityRemaining.toString(),
      '3.7655',
    );
  });

  testWidgets('discard requires confirmation; cancel does not mutate', (
    tester,
  ) async {
    final fixture = await _fixture(
      pantryItem(
        status: PantryItemStatus.reserved,
        quantityInitial: '10',
        quantityRemaining: '5',
      ),
    );
    final pending = Completer<PantryItem>();
    fixture.repository.onDiscard = (_, _) => pending.future;
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId');
    await _pump(tester);
    await tester.tap(find.byKey(const ValueKey('pantry-discard')));
    await tester.pumpAndSettle();
    expect(find.text(AppStrings.pantryDiscardWarning), findsOneWidget);
    expect(fixture.repository.discardRequests, isEmpty);
    await tester.tap(find.text(AppStrings.cancel).last);
    await tester.pumpAndSettle();
    expect(fixture.repository.discardRequests, isEmpty);

    await tester.tap(find.byKey(const ValueKey('pantry-discard')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('pantry-discard-note')),
      '  spoiled  ',
    );
    await tester.tap(find.byKey(const ValueKey('pantry-discard-confirm')));
    await tester.pump();
    expect(fixture.repository.discardRequests, hasLength(1));
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('pantry-discard-confirm')),
          )
          .onPressed,
      isNull,
    );
    pending.complete(
      pantryItem(
        quantityInitial: '10',
        quantityRemaining: '0',
        status: PantryItemStatus.discarded,
        closedAt: DateTime(2026, 1, 2),
      ),
    );
    await tester.pumpAndSettle();
    expect(fixture.repository.discardRequests.single.$2.toJson(), {
      'note': 'spoiled',
    });
    expect(
      fixture.controller.detailState.item?.status,
      PantryItemStatus.discarded,
    );
    expect(find.byKey(const ValueKey('pantry-discard')), findsNothing);
  });
}

Future<
  ({
    GoRouter router,
    Widget app,
    FakePantryRepository repository,
    PantryController controller,
  })
>
_fixture(PantryItem item) async {
  final repository = FakePantryRepository()..detail = item;
  final session = SessionController(
    authRepository: FakeAuthRepository(
      refreshError: const ApiHttpException(401),
    ),
    refreshTokenStore: NoRefreshTokenStore(),
    isWeb: true,
  );
  await session.login('user@example.test', 'Password123!');
  final controller = PantryController(repository: repository);
  final router = AppRouter(session, pantryController: controller).router;
  addTearDown(() {
    router.dispose();
    controller.dispose();
  });
  return (
    router: router,
    app: MaterialApp.router(routerConfig: router),
    repository: repository,
    controller: controller,
  );
}

Future<void> _pump(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}
