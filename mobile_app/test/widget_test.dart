import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

import 'support/fake_auth_repository.dart';

void main() {
  testWidgets('keeps the protected route in startup state while restoring', (
    tester,
  ) async {
    final refresh = Completer<void>();
    final repository = _DelayedRefreshRepository(refresh.future);
    final session = SessionController(
      authRepository: repository,
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));

    expect(find.bySemanticsLabel('Restoring session'), findsOneWidget);
    expect(find.text('Welcome back'), findsNothing);

    refresh.complete();
    await tester.pumpAndSettle();

    expect(find.text('Food Catalog is coming in P8.7.'), findsOneWidget);
  });

  testWidgets('anonymous protected startup redirects to login', (tester) async {
    final session = SessionController(
      authRepository: FakeAuthRepository(
        refreshError: const ApiHttpException(401),
      ),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));
    await tester.pumpAndSettle();

    expect(find.text('Welcome back'), findsOneWidget);
  });

  testWidgets('sign out clears authenticated state and returns to login', (
    tester,
  ) async {
    final session = SessionController(
      authRepository: FakeAuthRepository(),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Sign out').first);
    await tester.pumpAndSettle();

    expect(find.text('Welcome back'), findsOneWidget);
  });
}

final class _DelayedRefreshRepository extends FakeAuthRepository {
  _DelayedRefreshRepository(this._delay);

  final Future<void> _delay;

  @override
  Future<AccessSession> refreshWeb(CsrfToken csrfToken) async {
    await _delay;
    return FakeAuthRepository.session();
  }
}
