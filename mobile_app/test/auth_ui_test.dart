import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/auth/presentation/auth_pages.dart';

import 'support/fake_auth_repository.dart';

void main() {
  testWidgets('login validates email and a required password', (tester) async {
    final session = _session(FakeAuthRepository());
    await tester.pumpWidget(_page(LoginPage(sessionController: session)));

    await tester.tap(find.text('Sign in'));
    await tester.pump();

    expect(find.text('Enter your email.'), findsOneWidget);
    expect(find.text('Enter a password.'), findsOneWidget);
  });

  testWidgets('failed login preserves entered credentials for correction', (
    tester,
  ) async {
    final repository = FakeAuthRepository(
      loginError: const ApiHttpException(401),
    );
    final session = _session(repository);
    await tester.pumpWidget(_page(LoginPage(sessionController: session)));
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'person@example.test');
    await tester.enterText(fields.at(1), 'a password');

    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    expect(find.text('Email or password is incorrect.'), findsOneWidget);
    expect(
      tester.widget<TextFormField>(fields.at(0)).controller!.text,
      'person@example.test',
    );
    expect(
      tester.widget<TextFormField>(fields.at(1)).controller!.text,
      'a password',
    );
  });

  testWidgets('successful login authenticates the session', (tester) async {
    final repository = FakeAuthRepository();
    final session = _session(repository);
    await tester.pumpWidget(_page(LoginPage(sessionController: session)));
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'person@example.test');
    await tester.enterText(fields.at(1), 'a password');

    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    expect(session.isAuthenticated, isTrue);
    expect(repository.loginCalls, 1);
  });

  testWidgets('registration applies UTF-8 password byte validation', (
    tester,
  ) async {
    final session = _session(FakeAuthRepository());
    await tester.pumpWidget(_registrationApp(session));
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'Planner');
    await tester.enterText(fields.at(1), 'person@example.test');
    await tester.enterText(fields.at(2), '12345678901');

    await tester.tap(find.text('Create account'));
    await tester.pump();
    expect(
      find.text('Password must contain at least 12 characters.'),
      findsOneWidget,
    );

    await tester.enterText(
      fields.at(2),
      '😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀',
    );
    final oversizedEmojiPassword = List<String>.filled(
      19,
      String.fromCharCode(0x1f600),
    ).join();
    await tester.enterText(fields.at(2), oversizedEmojiPassword);
    await tester.tap(find.text('Create account'));
    await tester.pump();
    expect(
      find.text('Password must be 72 UTF-8 bytes or fewer.'),
      findsOneWidget,
    );
  });

  testWidgets('valid multibyte registration transitions to verification', (
    tester,
  ) async {
    final repository = FakeAuthRepository();
    final session = _session(repository);
    await tester.pumpWidget(_registrationApp(session));
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'Planner');
    await tester.enterText(fields.at(1), 'person@example.test');
    await tester.enterText(fields.at(2), '😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀');

    final validEmojiPassword = List<String>.filled(
      17,
      String.fromCharCode(0x1f600),
    ).join();
    await tester.enterText(fields.at(2), validEmojiPassword);
    await tester.tap(find.text('Create account'));
    await tester.pumpAndSettle();

    expect(repository.registerCalls, 1);
    expect(find.text('Verification destination'), findsOneWidget);
  });

  testWidgets('verification uses deep-link token and resend email', (
    tester,
  ) async {
    final repository = FakeAuthRepository();
    final session = _session(repository);
    final token = List<String>.filled(64, 'a').join();
    final router = GoRouter(
      initialLocation:
          '/auth/verify-email?token=$token&email=person@example.test',
      routes: [
        GoRoute(
          path: '/auth/verify-email',
          builder: (_, state) => VerifyEmailPage(
            sessionController: session,
            token: state.uri.queryParameters['token'],
            email: state.uri.queryParameters['email'],
          ),
        ),
      ],
    );
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));

    await tester.tap(find.text('Verify email'));
    await tester.pumpAndSettle();
    expect(repository.lastToken, token);
    expect(find.text('Email verified. You can now sign in.'), findsOneWidget);

    await tester.tap(find.text('Resend verification email'));
    await tester.pumpAndSettle();
    expect(repository.resendCalls, 1);
  });

  testWidgets('forgot password gives a generic success response', (
    tester,
  ) async {
    final repository = FakeAuthRepository();
    await tester.pumpWidget(
      _page(ForgotPasswordPage(sessionController: _session(repository))),
    );
    await tester.enterText(find.byType(TextFormField), 'person@example.test');

    await tester.tap(find.text('Send reset instructions'));
    await tester.pumpAndSettle();

    expect(repository.forgotCalls, 1);
    expect(
      find.text(
        'If an account matches this email, reset instructions have been sent.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('reset password accepts UTF-8-safe input and returns to login', (
    tester,
  ) async {
    final repository = FakeAuthRepository();
    final session = _session(repository);
    final token = 'reset-token';
    final router = GoRouter(
      initialLocation: '/auth/reset-password?token=$token',
      routes: [
        GoRoute(
          path: '/auth/reset-password',
          builder: (_, state) => ResetPasswordPage(
            sessionController: session,
            token: state.uri.queryParameters['token'],
          ),
        ),
        GoRoute(
          path: '/auth/login',
          builder: (_, _) => const Scaffold(body: Text('Login destination')),
        ),
      ],
    );
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    await tester.enterText(
      find.byType(TextFormField),
      '😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀',
    );

    final validEmojiPassword = List<String>.filled(
      17,
      String.fromCharCode(0x1f600),
    ).join();
    await tester.enterText(find.byType(TextFormField), validEmojiPassword);
    await tester.tap(find.text('Reset password'));
    await tester.pumpAndSettle();

    expect(repository.resetCalls, 1);
    expect(repository.lastToken, token);
    expect(find.text('Login destination'), findsOneWidget);
  });

  testWidgets('anonymous intended route is restored after login', (
    tester,
  ) async {
    final session = _session(
      FakeAuthRepository(refreshError: const ApiHttpException(401)),
    );
    await tester.pumpWidget(SmartMealPlannerApp(sessionController: session));
    await tester.pumpAndSettle();

    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'person@example.test');
    await tester.enterText(fields.at(1), 'a password');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    expect(find.text('Food Catalog is coming in P8.7.'), findsOneWidget);
  });

  testWidgets('accepts a relative catalog intended route', (tester) async {
    final router = await _authenticatedRouter();
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));

    router.go('/auth/login?from=${Uri.encodeComponent('/catalog/foods')}');
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.toString(),
      '/catalog/foods',
    );

    router.go(
      '/auth/login?from=${Uri.encodeComponent('/catalog/foods?view=grid')}',
    );
    await tester.pumpAndSettle();

    expect(
      router.routerDelegate.currentConfiguration.uri.toString(),
      '/catalog/foods?view=grid',
    );
  });

  testWidgets('rejects non-relative or unrelated intended routes', (
    tester,
  ) async {
    final router = await _authenticatedRouter();
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));

    const rejected = [
      'https://evil.example/catalog/foods',
      '//evil.example/catalog/foods',
      'https://user@evil.example/catalog/foods',
      'https://localhost/catalog/foods',
      'javascript:whatever',
      '/auth/login',
      '/catalog/unknown',
    ];

    for (final intended in rejected) {
      router.go('/auth/login?from=${Uri.encodeComponent(intended)}');
      await tester.pumpAndSettle();

      expect(
        router.routerDelegate.currentConfiguration.uri.toString(),
        '/catalog/foods',
        reason: 'rejected intended route: $intended',
      );
    }
  });

  testWidgets('unknown routes render the not-found screen', (tester) async {
    final session = _session(FakeAuthRepository());
    final router = AppRouter(session).router;
    await tester.pumpWidget(MaterialApp.router(routerConfig: router));
    router.go('/not-a-route');
    await tester.pumpAndSettle();

    expect(find.text('Page not found'), findsOneWidget);
  });
}

SessionController _session(FakeAuthRepository repository) => SessionController(
  authRepository: repository,
  refreshTokenStore: NoRefreshTokenStore(),
  isWeb: true,
);

Future<GoRouter> _authenticatedRouter() async {
  final session = _session(FakeAuthRepository());
  await session.login('person@example.test', 'a password');
  return AppRouter(session).router;
}

Widget _page(Widget child) => MaterialApp(home: child);

Widget _registrationApp(SessionController session) {
  final router = GoRouter(
    initialLocation: '/auth/register',
    routes: [
      GoRoute(
        path: '/auth/register',
        builder: (_, _) => RegistrationPage(sessionController: session),
      ),
      GoRoute(
        path: '/auth/verify-email',
        builder: (_, _) =>
            const Scaffold(body: Text('Verification destination')),
      ),
    ],
  );
  return MaterialApp.router(routerConfig: router);
}
