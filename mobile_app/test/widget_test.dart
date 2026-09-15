import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/auth_repository.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';

void main() {
  testWidgets('shows bootstrap UI before deciding the session state', (
    tester,
  ) async {
    final refresh = Completer<AccessSession>();
    final session = SessionController(
      authRepository: _AppAuthRepository(refreshResult: refresh.future),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));

    expect(find.bySemanticsLabel('Restoring session'), findsOneWidget);

    refresh.complete(_webSession());
    await tester.pumpAndSettle();

    expect(find.text('Signed in as person@example.test'), findsOneWidget);
  });

  testWidgets('shows anonymous UI after normal restore failure', (
    tester,
  ) async {
    final session = SessionController(
      authRepository: _AppAuthRepository(refreshError: true),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));
    await tester.pumpAndSettle();

    expect(find.text('Sign in is available in the next step.'), findsOneWidget);
  });

  testWidgets('sign out clears the authenticated app state', (tester) async {
    final session = SessionController(
      authRepository: _AppAuthRepository(),
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );

    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sign out'));
    await tester.pumpAndSettle();

    expect(find.text('Sign in is available in the next step.'), findsOneWidget);
  });
}

AccessSession _webSession() => AccessSession(
  accessToken: 'access',
  accessTokenExpiresAt: DateTime.utc(2030),
  refreshTokenExpiresAt: DateTime.utc(2030, 1, 2),
);

class _AppAuthRepository implements AuthRepository {
  _AppAuthRepository({this.refreshResult, this.refreshError = false});

  final Future<AccessSession>? refreshResult;
  final bool refreshError;

  @override
  Future<CsrfToken> fetchCsrf() async =>
      const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf');

  @override
  Future<AccessSession> refreshWeb(CsrfToken csrfToken) async {
    if (refreshError) {
      throw const ApiHttpException(401);
    }
    return refreshResult ?? _webSession();
  }

  @override
  Future<AuthIdentity> me() async => const AuthIdentity(
    publicId: 'a0a5b5ef-82bf-4d9f-9fe4-f07ad9bfec14',
    email: 'person@example.test',
    roles: ['ROLE_USER'],
  );

  @override
  Future<void> logoutWeb(CsrfToken csrfToken) async {}
  @override
  Future<void> forgotPassword(String email) async {}
  @override
  Future<AccessSession> loginAndroid(String email, String password) =>
      Future.value(_webSession());
  @override
  Future<AccessSession> loginWeb(String email, String password) =>
      Future.value(_webSession());
  @override
  Future<void> logoutAll() async {}
  @override
  Future<void> logoutAndroid(String refreshToken) async {}
  @override
  Future<AccessSession> refreshAndroid(String refreshToken) =>
      Future.value(_webSession());
  @override
  Future<void> register({
    required String email,
    required String password,
    required String displayName,
  }) async {}
  @override
  Future<void> resendVerification(String email) async {}
  @override
  Future<void> resetPassword({
    required String token,
    required String password,
  }) async {}
  @override
  Future<void> verifyEmail(String token) async {}
}
