import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

import 'support/fake_auth_repository.dart';
import 'support/fake_pantry_repository.dart';

void main() {
  testWidgets(
    'list loading, populated distinct lots, expiry and unit display',
    (tester) async {
      final pending = Completer<List<PantryItem>>();
      final repository = FakePantryRepository()..onList = (_) => pending.future;
      final fixture = await _fixture(repository);
      await tester.pumpWidget(fixture.app);
      fixture.router.go('/pantry');
      await _pump(tester);
      expect(find.byKey(const ValueKey('pantry-loading')), findsOneWidget);
      pending.complete([
        pantryItem(expiryDate: DateTime(2020, 2, 29)),
        pantryItem(
          publicId: testPantryIdTwo,
          unitDisplayName: '',
          unitCode: 'crate',
        ),
      ]);
      await _pump(tester);
      expect(find.text('Gạo'), findsNWidgets(2));
      expect(
        find.byKey(const ValueKey('pantry-item-$testPantryId')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('pantry-item-$testPantryIdTwo')),
        findsOneWidget,
      );
      expect(find.textContaining('0.0001 bao'), findsOneWidget);
      expect(find.textContaining('0.0001 crate'), findsOneWidget);
      expect(find.textContaining('2020-02-29'), findsOneWidget);
      expect(find.text(AppStrings.pantryStatusAvailable), findsNWidgets(2));
      expect(find.text(AppStrings.pantryStatusExpired), findsNothing);
    },
  );

  testWidgets('empty, error retry, include closed, and refresh', (
    tester,
  ) async {
    final repository = FakePantryRepository()..items = [];
    final fixture = await _fixture(repository);
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry');
    await _pump(tester);
    expect(find.text(AppStrings.pantryNoOpenItems), findsOneWidget);
    expect(find.text(AppStrings.pantrySubtitle), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pantry-include-closed')));
    await _pump(tester);
    expect(find.text(AppStrings.pantryNoItems), findsOneWidget);
    expect(find.text(AppStrings.pantryHistorySubtitle), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pantry-include-closed')));
    await _pump(tester);
    expect(find.text(AppStrings.pantryNoOpenItems), findsOneWidget);
    repository.listError = const ApiTransportException(
      ApiTransportFailureKind.network,
    );
    await tester.tap(find.byKey(const ValueKey('pantry-refresh')));
    await _pump(tester);
    expect(find.byKey(const ValueKey('pantry-retry')), findsOneWidget);
    expect(find.text(AppStrings.unableToReachService), findsOneWidget);
    repository.listError = null;
    repository.items = [pantryItem()];
    await tester.tap(find.byKey(const ValueKey('pantry-retry')));
    await _pump(tester);
    expect(
      find.byKey(const ValueKey('pantry-item-$testPantryId')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('pantry-include-closed')));
    await _pump(tester);
    expect(repository.listRequests.last, isTrue);
    expect(find.text(AppStrings.pantryHistorySubtitle), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pantry-refresh')));
    await _pump(tester);
    expect(repository.listRequests.last, isTrue);
  });

  testWidgets('detail loading, error retry, nullable and closed fields', (
    tester,
  ) async {
    final pending = Completer<PantryItem>();
    final repository = FakePantryRepository()..onDetail = (_) => pending.future;
    final fixture = await _fixture(repository);
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId');
    await _pump(tester);
    expect(find.byKey(const ValueKey('pantry-detail-loading')), findsOneWidget);
    pending.completeError(const ApiHttpException(404));
    await _pump(tester);
    expect(find.text(AppStrings.pantryNotFound), findsOneWidget);
    repository.onDetail = (_) async => pantryItem(
      status: PantryItemStatus.consumed,
      closedAt: DateTime(2026, 1, 2, 12, 30),
      acquiredOnPresent: false,
    );
    await tester.tap(find.byKey(const ValueKey('pantry-detail-retry')));
    await _pump(tester);
    expect(find.text('Gạo'), findsOneWidget);
    await tester.drag(
      find.byKey(const ValueKey('pantry-detail-$testPantryId')),
      const Offset(0, -500),
    );
    await _pump(tester);
    expect(find.text(AppStrings.pantryStatusConsumed), findsOneWidget);
    await tester.drag(
      find.byKey(const ValueKey('pantry-detail-$testPantryId')),
      const Offset(0, -500),
    );
    await _pump(tester);
    expect(find.text('2026-01-02 12:30'), findsOneWidget);
    expect(find.text(AppStrings.pantryNotSet), findsWidgets);
  });

  testWidgets('detail shows mapped food, dates and expiry metadata', (
    tester,
  ) async {
    final repository = FakePantryRepository()
      ..detail = pantryItem(
        foodName: 'Gạo trắng',
        expiryDate: DateTime(2026, 2, 3),
        note: 'Lô riêng',
      );
    final fixture = await _fixture(repository);
    await tester.binding.setSurfaceSize(const Size(1100, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(fixture.app);
    fixture.router.go('/pantry/$testPantryId');
    await _pump(tester);
    expect(find.text('Gạo trắng'), findsOneWidget);
    expect(find.textContaining('1.2345 bao'), findsOneWidget);
    expect(find.text('2026-01-01'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('2026-02-03'),
      150,
      scrollable: find.byType(Scrollable).last,
    );
    expect(find.text('2026-02-03'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text(AppStrings.pantryExpiryUseBy),
      150,
      scrollable: find.byType(Scrollable).last,
    );
    expect(find.text(AppStrings.pantryExpiryUseBy), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('Lô riêng'),
      150,
      scrollable: find.byType(Scrollable).last,
    );
    expect(find.text('Lô riêng'), findsOneWidget);
  });

  testWidgets(
    'drawer and rail Pantry destination open list and stay selected on detail',
    (tester) async {
      final repository = FakePantryRepository();
      final fixture = await _fixture(repository);
      await tester.binding.setSurfaceSize(const Size(600, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(fixture.app);
      await _pump(tester);
      await tester.tap(find.byIcon(Icons.menu));
      await tester.pumpAndSettle();
      final drawerTile = tester.widget<ListTile>(
        find.byKey(const ValueKey('pantry-nav-drawer')),
      );
      expect(drawerTile.selected, isFalse);
      await tester.tap(find.byKey(const ValueKey('pantry-nav-drawer')));
      await tester.pumpAndSettle();
      expect(
        fixture.router.routerDelegate.currentConfiguration.uri.path,
        '/pantry',
      );
      fixture.router.go('/pantry/$testPantryId');
      await _pump(tester);
      await tester.tap(find.byIcon(Icons.menu));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<ListTile>(find.byKey(const ValueKey('pantry-nav-drawer')))
            .selected,
        isTrue,
      );

      await tester.pumpWidget(const SizedBox.shrink());
      await tester.binding.setSurfaceSize(const Size(1100, 800));
      await tester.pumpWidget(fixture.app);
      fixture.router.go('/pantry/$testPantryId');
      await _pump(tester);
      expect(
        tester
            .widget<NavigationRail>(find.byType(NavigationRail))
            .selectedIndex,
        6,
      );
      await tester.tap(find.byKey(const ValueKey('pantry-nav-rail')));
      await _pump(tester);
      expect(
        fixture.router.routerDelegate.currentConfiguration.uri.path,
        '/pantry',
      );
      await tester.binding.setSurfaceSize(null);
    },
  );

  testWidgets('logout clears app-owned Pantry state and ignores old read', (
    tester,
  ) async {
    final pending = Completer<List<PantryItem>>();
    final repository = FakePantryRepository()..onList = (_) => pending.future;
    final session = SessionController(
      authRepository: FakeAuthRepository(),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );
    await session.login('user@example.test', 'Password123!');
    final controller = PantryController(repository: repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      SmartMealPlannerApp(
        sessionController: session,
        pantryController: controller,
      ),
    );
    await _pump(tester);
    final oldRead = controller.loadInitial();
    await session.logout();
    await _pump(tester);
    pending.complete([pantryItem()]);
    await oldRead;
    await _pump(tester);
    expect(controller.listState.items, isEmpty);
    expect(
      find.byKey(const ValueKey('pantry-item-$testPantryId')),
      findsNothing,
    );
  });

  testWidgets(
    'principal change reloads Pantry and ignores prior account response',
    (tester) async {
      final oldRead = Completer<List<PantryItem>>();
      final repository = FakePantryRepository();
      repository.onList = (_) => repository.listRequests.length == 1
          ? oldRead.future
          : Future.value([pantryItem(publicId: testPantryIdTwo)]);
      final auth = _SwitchableAuthRepository();
      final session = SessionController(
        authRepository: auth,
        refreshTokenStore: NoRefreshTokenStore(),
        isWeb: true,
      );
      await session.login('first@example.test', 'Password123!');
      final controller = PantryController(repository: repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        SmartMealPlannerApp(
          sessionController: session,
          pantryController: controller,
        ),
      );
      await _pump(tester);
      await tester.tap(find.byKey(const ValueKey('pantry-nav-rail')));
      await _pump(tester);
      expect(repository.listRequests, [false]);
      auth.publicId = '00000000-0000-4000-8000-000000000302';
      await session.login('second@example.test', 'Password123!');
      await _pump(tester);
      expect(controller.listState.items.single.publicId, testPantryIdTwo);
      oldRead.complete([pantryItem()]);
      await _pump(tester);
      expect(controller.listState.items.single.publicId, testPantryIdTwo);
    },
  );
}

final class _SwitchableAuthRepository extends FakeAuthRepository {
  String publicId = '00000000-0000-4000-8000-000000000301';

  @override
  Future<AuthIdentity> me() async => AuthIdentity(
    publicId: publicId,
    email: 'person@example.test',
    roles: const ['ROLE_USER'],
  );
}

Future<({GoRouter router, Widget app})> _fixture(
  FakePantryRepository repository,
) async {
  final session = await _session();
  final controller = PantryController(repository: repository);
  final router = AppRouter(session, pantryController: controller).router;
  addTearDown(() {
    router.dispose();
    controller.dispose();
  });
  return (router: router, app: MaterialApp.router(routerConfig: router));
}

Future<SessionController> _session() async {
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

Future<void> _pump(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 1));
}
