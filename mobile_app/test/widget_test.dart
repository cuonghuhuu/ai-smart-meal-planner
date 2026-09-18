import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

import 'support/fake_auth_repository.dart';

void main() {
  testWidgets('configures Vietnamese Material localization', (tester) async {
    final session = SessionController(
      authRepository: FakeAuthRepository(
        refreshError: const ApiHttpException(401),
      ),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));

    final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.locale, const Locale('vi', 'VN'));
  });

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

    expect(
      find.bySemanticsLabel('Đang khôi phục phiên đăng nhập'),
      findsOneWidget,
    );
    expect(find.text('Chào mừng bạn quay lại'), findsNothing);

    refresh.complete();
    await tester.pumpAndSettle();

    expect(
      find.text('Danh mục thực phẩm đang được hoàn thiện.'),
      findsOneWidget,
    );
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

    expect(find.text('Chào mừng bạn quay lại'), findsOneWidget);
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
    await tester.tap(find.byTooltip('Đăng xuất').first);
    await tester.pumpAndSettle();

    expect(find.text('Chào mừng bạn quay lại'), findsOneWidget);
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
