import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/data/refresh_token_store.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/profile/data/reference_data_repository.dart';
import 'package:smart_meal_planner/features/profile/presentation/profile_page.dart';

import 'support/fake_auth_repository.dart';

void main() {
  testWidgets('authenticated /profile opens Profile page', (tester) async {
    final session = await _authenticatedSession();
    final controller = _controller();
    final router = AppRouter(session, profileController: controller).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/profile');
    await tester.pumpAndSettle();

    expect(
      find.text('Complete your profile to personalize your meal plans.'),
      findsOneWidget,
    );
  });

  testWidgets('anonymous /profile redirects to login', (tester) async {
    final session = await _anonymousSession();
    final router = AppRouter(session, profileController: _controller()).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/profile');
    await tester.pumpAndSettle();

    expect(find.text('Welcome back'), findsOneWidget);
  });

  testWidgets('intended /profile is restored after login', (tester) async {
    final auth = FakeAuthRepository(refreshError: const ApiHttpException(401));
    final session = SessionController(
      authRepository: auth,
      refreshTokenStore: NoRefreshTokenStore(),
      isWeb: true,
    );
    await session.bootstrap();
    final router = AppRouter(session, profileController: _controller()).router;
    await tester.pumpWidget(_routerApp(router));
    router.go('/profile');
    await tester.pumpAndSettle();
    final emailField = _fieldWithLabel('Email');
    final passwordField = _fieldWithLabel('Password');
    expect(emailField, findsOneWidget);
    expect(passwordField, findsOneWidget);
    await tester.enterText(emailField, 'user@example.test');
    await tester.enterText(passwordField, 'Password123!');
    await tester.tap(find.text('Sign in'));
    await tester.pumpAndSettle();

    expect(
      find.text('Complete your profile to personalize your meal plans.'),
      findsOneWidget,
    );
  });

  testWidgets('external intended profile targets are rejected', (tester) async {
    final session = await _authenticatedSession();
    final router = AppRouter(session, profileController: _controller()).router;
    await tester.pumpWidget(_routerApp(router));
    for (final target in [
      'https://evil.example/profile',
      '//evil.example/profile',
      'https://user@evil.example/profile',
      'javascript:anything',
    ]) {
      router.go('/auth/login?from=${Uri.encodeComponent(target)}');
      await tester.pumpAndSettle();
      expect(find.text('Food Catalog is coming in P8.7.'), findsOneWidget);
    }
  });

  testWidgets('Profile navigation item opens /profile', (tester) async {
    final session = await _authenticatedSession();
    final router = AppRouter(session, profileController: _controller()).router;
    await tester.pumpWidget(_routerApp(router));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Profile'));
    await tester.pumpAndSettle();

    expect(
      find.text('Complete your profile to personalize your meal plans.'),
      findsOneWidget,
    );
  });

  testWidgets('existing profile values populate the form', (tester) async {
    final controller = _controller(profile: _profile());
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    expect(find.text('172'), findsOneWidget);
    expect(find.text('Moderately Active'), findsOneWidget);
    expect(find.text('Maintain Weight'), findsOneWidget);
    expect(find.text('Existing notes'), findsOneWidget);
  });

  testWidgets('404 profile shows an editable default form', (tester) async {
    final controller = _controller(notFound: true);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    expect(
      find.text('Complete your profile to personalize your meal plans.'),
      findsOneWidget,
    );
    expect(find.text('1'), findsOneWidget);
    expect(find.text('Save profile'), findsOneWidget);
  });

  testWidgets('blank weekly change can be saved as null', (tester) async {
    final repository = FakeProfileRepository(
      profile: _profile(weeklyChangeKg: null),
    );
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();

    final weeklyField = _fieldWithLabel('Weekly weight change goal (kg/week)');
    expect(weeklyField, findsOneWidget);
    expect(tester.widget<TextFormField>(weeklyField).controller!.text, isEmpty);
    await _tapAfterScroll(
      tester,
      find.widgetWithText(FilledButton, 'Save profile'),
    );
    await tester.pumpAndSettle();

    expect(repository.updateCalls, 1);
    expect(repository.lastDraft?.weeklyChangeKg, isNull);
  });

  testWidgets('invalid form prevents save and valid form calls repository', (
    tester,
  ) async {
    final repository = FakeProfileRepository(profile: _profile());
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();
    final heightField = _fieldWithLabel('Height (cm)');
    expect(heightField, findsOneWidget);
    await tester.enterText(heightField, '30');
    expect(tester.widget<TextFormField>(heightField).controller!.text, '30');
    final saveFinder = find.widgetWithText(FilledButton, 'Save profile');
    expect(saveFinder, findsOneWidget);
    final saveButton = tester.widget<FilledButton>(saveFinder);
    expect(saveButton.onPressed, isNotNull);
    saveButton.onPressed!();
    await tester.pump();
    expect(repository.updateCalls, 0);
    expect(find.textContaining('greater than 30'), findsOneWidget);

    await tester.enterText(heightField, '175');
    expect(tester.widget<TextFormField>(heightField).controller!.text, '175');
    await tester.pump();
    saveButton.onPressed!();
    await tester.pump();
    await tester.pump();
    expect(repository.updateCalls, 1);
  });

  testWidgets('conflict displays Reload and reload replaces stale state', (
    tester,
  ) async {
    final repository = FakeProfileRepository(
      profile: _profile(),
      updateError: const ApiHttpException(409),
    );
    final controller = _controller(repository: repository);
    await tester.pumpWidget(_pageApp(controller));
    await tester.pumpAndSettle();
    await _tapAfterScroll(
      tester,
      find.widgetWithText(FilledButton, 'Save profile'),
    );
    await tester.pumpAndSettle();

    expect(find.text('Reload'), findsOneWidget);
    repository.updateError = null;
    repository.profile = _profile(heightCm: 180);
    await _tapAfterScroll(tester, find.widgetWithText(TextButton, 'Reload'));
    await tester.pumpAndSettle();
    expect(find.text('180'), findsOneWidget);
    expect(find.text('172'), findsNothing);
    expect(
      find.text(
        'Your profile was changed elsewhere. Reload the latest version before saving.',
      ),
      findsNothing,
    );
  });

  testWidgets('profile form remains usable at a narrow width', (tester) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.binding.setSurfaceSize(const Size(360, 800));
    await tester.pumpWidget(_pageApp(_controller(profile: _profile())));
    await tester.pumpAndSettle();

    expect(find.byType(Scrollable), findsWidgets);
    await tester.ensureVisible(
      find.widgetWithText(FilledButton, 'Save profile'),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
    expect(find.text('Save profile'), findsOneWidget);
  });
}

Future<void> _tapAfterScroll(WidgetTester tester, Finder target) async {
  expect(target, findsOneWidget);

  final scrollView = find.byType(SingleChildScrollView);
  expect(scrollView, findsOneWidget);

  await tester.dragUntilVisible(target, scrollView, const Offset(0, -200));

  await tester.pump();

  await tester.tap(target);
  await tester.pump();
}

Finder _fieldWithLabel(String label) =>
    find.ancestor(of: find.text(label), matching: find.byType(TextFormField));

Widget _routerApp(GoRouter router) => MaterialApp.router(routerConfig: router);

Widget _pageApp(ProfileController controller) => MaterialApp(
  home: ProfilePage(
    sessionController: _sessionForPage(),
    profileController: controller,
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

SessionController _sessionForPage() => SessionController(
  authRepository: FakeAuthRepository(),
  refreshTokenStore: NoRefreshTokenStore(),
  isWeb: true,
);

ProfileController _controller({
  FakeProfileRepository? repository,
  Profile? profile,
  bool notFound = false,
}) {
  final profileRepository =
      repository ??
      FakeProfileRepository(
        profile: profile,
        notFound: notFound || profile == null,
      );
  return ProfileController(
    profileRepository: profileRepository,
    referenceDataRepository: const FakeReferenceDataRepository(),
  );
}

Profile _profile({double heightCm = 172, double? weeklyChangeKg = 0}) =>
    Profile(
      birthDate: DateTime(1995, 2, 3),
      sex: 'FEMALE',
      heightCm: heightCm,
      activityLevel: 'MODERATE',
      nutritionGoal: 'MAINTAIN',
      targetWeightKg: 65,
      weeklyChangeKg: weeklyChangeKg,
      householdSize: 2,
      maxCookMinutes: 30,
      notes: 'Existing notes',
      version: 4,
      createdAt: DateTime(2026, 1, 1),
      updatedAt: DateTime(2026, 1, 2),
    );

final class FakeProfileRepository implements ProfileRepository {
  FakeProfileRepository({
    this.profile,
    this.notFound = false,
    this.updateError,
  });

  Profile? profile;
  final bool notFound;
  Object? updateError;
  var updateCalls = 0;
  ProfileDraft? lastDraft;

  @override
  Future<Profile> getProfile() async {
    if (notFound) throw const ProfileNotFoundException();
    return profile!;
  }

  @override
  Future<Profile> updateProfile(ProfileDraft draft) async {
    updateCalls++;
    lastDraft = draft;
    if (updateError != null) throw updateError!;
    return profile!;
  }
}

final class FakeReferenceDataRepository implements ReferenceDataRepository {
  const FakeReferenceDataRepository();

  @override
  Future<List<ActivityLevelReference>> getActivityLevels() async => const [
    ActivityLevelReference(
      code: 'MODERATE',
      displayName: 'Moderately Active',
      description: 'Some movement',
      energyFactor: 1.55,
      displayOrder: 1,
    ),
  ];

  @override
  Future<List<NutritionGoalReference>> getNutritionGoals() async => const [
    NutritionGoalReference(
      code: 'MAINTAIN',
      displayName: 'Maintain Weight',
      description: 'Keep weight stable',
      displayOrder: 1,
    ),
  ];
}
